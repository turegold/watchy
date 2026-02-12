package com.watchparty.watchparty.room.service;

import com.watchparty.watchparty.common.exception.AppException;
import com.watchparty.watchparty.common.exception.ErrorCode;
import com.watchparty.watchparty.room.entity.Room;
import com.watchparty.watchparty.room.entity.RoomMember;
import com.watchparty.watchparty.room.repository.RoomMemberRepository;
import com.watchparty.watchparty.room.repository.RoomRepository;
import com.watchparty.watchparty.user.entity.User;
import com.watchparty.watchparty.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        log.info("Joined room: roomId={}, userId={}", roomId, userId);
    }

    public void validateHost(Long roomId, Long userId) {
        log.debug("Validating host: roomId={}, userId={}", roomId, userId);

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));

        if (!room.getHost().getId().equals(userId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "방장만 이 작업을 수행할 수 있습니다.");
        }
    }

    public void leaveRoom(Long roomId, Long userId) {
        log.info("Leaving room: roomId={}, userId={}", roomId, userId);

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        RoomMember member = roomMemberRepository.findByRoomAndUser(room, user)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_ROOM_MEMBER));

        roomMemberRepository.delete(member);
        List<RoomMember> remaining = roomMemberRepository.findByRoom(room);

        if (remaining.isEmpty()) {
            roomRepository.delete(room);
            log.info("Room deleted because it became empty: roomId={}", roomId);
            return;
        }

        if (room.getHost().equals(user)) {
            RoomMember nextHost = remaining.get(0);
            room.changeHost(nextHost.getUser());
            log.info("Host transferred: roomId={}, newHostUserId={}", roomId, nextHost.getUser().getId());
        }
    }

    @Transactional(readOnly = true)
    public List<Room> getRooms() {
        List<Room> rooms = roomRepository.findAll();
        log.debug("Rooms fetched: count={}", rooms.size());
        return rooms;
    }
}
