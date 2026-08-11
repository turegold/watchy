package com.watchparty.watchparty.room.service;

import com.watchparty.watchparty.chat.listener.RoomSystemMessageListener;
import com.watchparty.watchparty.common.exception.AppException;
import com.watchparty.watchparty.common.exception.ErrorCode;
import com.watchparty.watchparty.common.storage.S3StorageService;
import com.watchparty.watchparty.room.dto.RoomListItemResponse;
import com.watchparty.watchparty.room.entity.Room;
import com.watchparty.watchparty.room.repository.RoomHeartbeatRedisRepository;
import com.watchparty.watchparty.room.repository.RoomParticipantRedisRepository;
import com.watchparty.watchparty.room.repository.RoomRepository;
import com.watchparty.watchparty.user.entity.User;
import com.watchparty.watchparty.user.repository.UserRepository;
import com.watchparty.watchparty.video.service.VideoStateService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;
    private final VideoStateService videoStateService;
    private final S3StorageService s3StorageService;

    // 참여자(현재 접속) 관리는 DB 기반에서 Redis로 전환됨.
    // - 참여자 목록: RoomParticipantRedisRepository (ZSET, score=입장시각)
    // - 생존 신호  : RoomHeartbeatRedisRepository   (ZSET, score=마지막 하트비트 시각)
    private final RoomParticipantRedisRepository participantRedis;
    private final RoomHeartbeatRedisRepository heartbeatRedis;

    // 하트비트가 이 시간(ms) 넘게 없으면 죽은 것으로 간주 (스윕이 강제 퇴장)
    public static final long HEARTBEAT_TIMEOUT_MILLIS = 30_000L;

    // 방 생성
    public Room createRoom(Long hostUserId, String title, boolean isPrivate) {
        log.info("Creating room: hostUserId={}, title={}, isPrivate={}", hostUserId, title, isPrivate);

        User host = userRepository.findById(hostUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Room room = new Room(title, host, isPrivate);
        Room savedRoom = roomRepository.save(room);

        // 방장을 첫 참여자로 등록 + 하트비트 즉시 기록(막 만든 방이 스윕에 바로 지워지지 않도록)
        long now = System.currentTimeMillis();
        participantRedis.join(savedRoom.getId(), hostUserId);
        heartbeatRedis.record(savedRoom.getId(), hostUserId, now);

        log.info("Room created: roomId={}, hostUserId={}", savedRoom.getId(), hostUserId);
        return savedRoom;
    }

    // 방 참여
    public void joinRoom(Long roomId, Long userId) {
        log.info("Joining room: roomId={}, userId={}", roomId, userId);

        // Room row 쓰기 락 — join/leave/방삭제를 방 단위로 직렬화.
        // "마지막 유저 leave로 방이 막 삭제되는 순간의 join"을 여기서 원천 차단(방 없으면 ROOM_NOT_FOUND).
        Room room = entityManager.find(Room.class, roomId, LockModeType.PESSIMISTIC_WRITE);
        if (room == null) {
            throw new AppException(ErrorCode.ROOM_NOT_FOUND);
        }
        userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (participantRedis.isParticipant(roomId, userId)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "이미 방에 참여한 유저입니다.");
        }

        long now = System.currentTimeMillis();
        participantRedis.join(roomId, userId);
        heartbeatRedis.record(roomId, userId, now);

        eventPublisher.publishEvent(
                new RoomSystemMessageListener.RoomJoinedEvent(roomId, userId)
        );

        log.info("Joined room: roomId={}, userId={}", roomId, userId);
    }

    // 방장 검증 (방장 정보는 여전히 DB Room에 있음)
    public void validateHost(Long roomId, Long userId) {
        log.debug("Validating host: roomId={}, userId={}", roomId, userId);

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));

        if (!room.getHost().getId().equals(userId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "방장만 이 작업을 수행할 수 있습니다.");
        }
    }

    // 방 나가기 (사용자 명시 퇴장 / 하트비트 스윕 / WS 끊김 — 모두 이 하나로 수렴)
    // 여러 번 호출되거나 이미 나간 유저여도 안전(멱등)하게 동작한다.
    public void leaveRoom(Long roomId, Long userId) {
        log.info("Leaving room: roomId={}, userId={}", roomId, userId);

        // Room row 락 — 동시 leave를 직렬화(방장 위임/빈 방 삭제 정합성 보호)
        Room room = entityManager.find(Room.class, roomId, LockModeType.PESSIMISTIC_WRITE);
        if (room == null) {
            // 이미 삭제된 방 — 스윕/중복 호출 경쟁에서 조용히 무시
            heartbeatRedis.remove(roomId, userId);
            return;
        }

        // 이미 참여자가 아니면(중복 leave 등) 하트비트만 정리하고 종료
        if (!participantRedis.isParticipant(roomId, userId)) {
            heartbeatRedis.remove(roomId, userId);
            log.info("Leave skipped (참여자 아님): roomId={}, userId={}", roomId, userId);
            return;
        }

        boolean leavingHost = room.getHost() != null && room.getHost().getId().equals(userId);

        // 원자적 퇴장: 제거 + 남은 인원 수 + 다음 방장 후보(입장순 최상단) 한 번에
        RoomParticipantRedisRepository.LeaveResult result =
                participantRedis.leaveAndPickNextHost(roomId, userId);
        heartbeatRedis.remove(roomId, userId);

        eventPublisher.publishEvent(
                new RoomSystemMessageListener.RoomLeftEvent(roomId, userId)
        );

        // 0명이면 방 삭제 (DB Room + Redis 참여자/하트비트 + Redis 영상 상태)
        if (result.remainingCount() == 0L) {
            videoStateService.deleteState(roomId);
            participantRedis.deleteRoomParticipants(roomId);
            heartbeatRedis.delete(roomId);
            roomRepository.delete(room);
            log.info("Room deleted because it became empty: roomId={}", roomId);
            return;
        }

        // 방장이 나갔으면 다음 방장에게 위임
        if (leavingHost) {
            Long newHostId = result.nextHostUserId();
            if (newHostId == null) {
                throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "다음 방장 후보를 찾지 못했습니다.");
            }
            // DB 조회 없이 id 참조만으로 FK 갱신
            User newHost = userRepository.getReferenceById(newHostId);
            room.changeHost(newHost);

            eventPublisher.publishEvent(
                    new RoomSystemMessageListener.HostChangedEvent(roomId, userId, newHostId)
            );
            log.info("Host transferred: roomId={}, newHostUserId={}", roomId, newHostId);
        }

        log.info("Leave room completed: roomId={}, userId={}, remaining={}",
                roomId, userId, result.remainingCount());
    }

    // 하트비트 수신 — 현재 참여자일 때만 생존 시각 갱신(이미 나간 유저를 되살리지 않음)
    public void handleHeartbeat(Long roomId, Long userId) {
        if (participantRedis.isParticipant(roomId, userId)) {
            heartbeatRedis.record(roomId, userId, System.currentTimeMillis());
        }
    }

    // 스윕용: 하트비트가 끊긴(cutoff 이전) 유저 목록
    @Transactional(readOnly = true)
    public List<Long> findDeadParticipants(Long roomId, long cutoffMillis) {
        return heartbeatRedis.findDeadUserIds(roomId, cutoffMillis);
    }

    // 스윕용: 전체 방 id
    @Transactional(readOnly = true)
    public List<Long> findAllRoomIds() {
        return roomRepository.findAllRoomIds();
    }

    @Transactional(readOnly = true)
    public List<RoomListItemResponse> getRooms() {
        // 방 목록 + 방장 프로필 key는 findRoomsForList() 한 번의 join으로 가져온다(N+1 제거).
        // 참여자 수(Redis ZCARD) · 현재 videoId(Redis) · 프로필 key→presigned URL 변환만 방마다 별도로 채운다.
        return roomRepository.findRoomsForList().stream()
                .map(room -> {
                    long participantCount = participantRedis.count(room.getRoomId());
                    String videoId = videoStateService.getVideoId(room.getRoomId());
                    String rawImageKey = room.getHostProfileImageUrl();
                    String hostImageUrl = (rawImageKey == null || rawImageKey.isBlank())
                            ? null
                            : s3StorageService.createPresignedGetUrl(rawImageKey);
                    return room.withParticipantCount(participantCount)
                            .withVideoId(videoId)
                            .withHostProfileImageUrl(hostImageUrl);
                })
                .toList();
    }
}
