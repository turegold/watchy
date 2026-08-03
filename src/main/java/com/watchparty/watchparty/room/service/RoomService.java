package com.watchparty.watchparty.room.service;

import com.watchparty.watchparty.chat.listener.RoomSystemMessageListener;
import com.watchparty.watchparty.common.exception.AppException;
import com.watchparty.watchparty.common.exception.ErrorCode;
import com.watchparty.watchparty.room.dto.RoomListItemResponse;
import com.watchparty.watchparty.room.entity.Room;
import com.watchparty.watchparty.room.entity.RoomMember;
import com.watchparty.watchparty.room.repository.RoomMemberRepository;
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
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;
    private final VideoStateService videoStateService;

    // 방 생성
    public Room createRoom(Long hostUserId, String title, boolean isPrivate) {
        log.info("Creating room: hostUserId={}, title={}, isPrivate={}", hostUserId, title, isPrivate);

        User host = userRepository.findById(hostUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Room room = new Room(title, host, isPrivate);
        Room savedRoom = roomRepository.save(room);

        RoomMember hostMember = new RoomMember(savedRoom, host);
        roomMemberRepository.save(hostMember);

        log.info("Room created: roomId={}, hostUserId={}", savedRoom.getId(), hostUserId);
        return savedRoom;
    }

    // 방 참여
    public void joinRoom(Long roomId, Long userId) {
        log.info("Joining room: roomId={}, userId={}", roomId, userId);

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (roomMemberRepository.existsByRoomAndUser(room, user)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "이미 방에 참여한 유저입니다.");
        }

        RoomMember roomMember = new RoomMember(room, user);
        roomMemberRepository.save(roomMember);

        // 시스템 메시지 전송
        eventPublisher.publishEvent(
                new RoomSystemMessageListener.RoomJoinedEvent(roomId, userId)
        );

        log.info("Joined room: roomId={}, userId={}", roomId, userId);
    }

    // 방장 검증
    public void validateHost(Long roomId, Long userId) {
        log.debug("Validating host: roomId={}, userId={}", roomId, userId);

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));

        if (!room.getHost().getId().equals(userId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "방장만 이 작업을 수행할 수 있습니다.");
        }
    }

    // 방 나가기
    public void leaveRoom(Long roomId, Long userId) {
        log.info("Leaving room: roomId={}, userId={}", roomId, userId);

        // Room row 락 (leave 동시에 들어와도 한 번에 하나씩 처리되게)
        Room room = entityManager.find(Room.class, roomId, LockModeType.PESSIMISTIC_WRITE);
        if (room == null) {
            throw new AppException(ErrorCode.ROOM_NOT_FOUND);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        RoomMember member = roomMemberRepository.findByRoomAndUser(room, user)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_ROOM_MEMBER));

        boolean leavingHost = room.getHost() != null && room.getHost().getId().equals(userId);

        // RoomMember 삭제
        roomMemberRepository.delete(member);

        // 시스템 메시지 전송
        eventPublisher.publishEvent(
                new RoomSystemMessageListener.RoomLeftEvent(roomId, userId)
        );

        // 남은 인원 수 확인
        long remainingCount = roomMemberRepository.countByRoom(room);

        // 0명이면 방 삭제
        if (remainingCount == 0L) {
            // redis에서 삭제
            videoStateService.deleteState(roomId);
            // DB에서 삭제
            roomRepository.delete(room);
            log.info("Room deleted because it became empty: roomId={}", roomId);
            return;
        }

        // 방장이 나갔으면 방장 위임
        if (leavingHost) {
            Long oldHostId = userId;
            RoomMember nextHostMember = roomMemberRepository
                    .findTopByRoomOrderByJoinedAtAsc(room)
                    .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "다음 방장 후보를 찾지 못했습니다."));

            Long newHostId = nextHostMember.getUser().getId();
            room.changeHost(nextHostMember.getUser());

            // 시스템 메시지 전송
            eventPublisher.publishEvent(
                    new RoomSystemMessageListener.HostChangedEvent(roomId, oldHostId, newHostId)
            );

            log.info("Host transferred: roomId={}, newHostUserId={}", roomId, nextHostMember.getUser().getId());
        }

        log.info("Leave room completed: roomId={}, userId={}, remaining={}", roomId, userId, remainingCount);
    }

    @Transactional(readOnly = true)
    public List<RoomListItemResponse> getRooms() {
        // 방 목록(DB) + 각 방의 현재 videoId(Redis)를 합쳐 썸네일에 쓰도록 반환
        return roomRepository.findRoomsWithParticipantCount().stream()
                .map(room -> room.withVideoId(videoStateService.getVideoId(room.getRoomId())))
                .toList();
    }
}
