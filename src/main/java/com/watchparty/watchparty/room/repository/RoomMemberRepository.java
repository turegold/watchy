package com.watchparty.watchparty.room.repository;

import com.watchparty.watchparty.room.entity.Room;
import com.watchparty.watchparty.room.entity.RoomMember;
import com.watchparty.watchparty.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    // 특정 방에 참여한 모든 멤버 조회
    List<RoomMember> findByRoom(Room room);

    // 특정 유저가 특정 방에 참여했는지 확인
    Optional<RoomMember> findByRoomAndUser(Room room, User user);

    // 방 참여 여부만 빠르게 확인
    boolean existsByRoomAndUser(Room room, User user);
}
