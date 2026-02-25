package com.watchparty.watchparty.room.repository;

import com.watchparty.watchparty.room.dto.RoomListItemResponse;
import com.watchparty.watchparty.room.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {
    @Query("""
        select new com.watchparty.watchparty.room.dto.RoomListItemResponse(
            r.id,
            r.title,
            r.isPrivate,
            r.host.id,
            up.nickname,
            count(rm.id)
        )
        from Room r
        left join RoomMember rm on rm.room = r
        left join UserProfile up on up.user = r.host
        group by r.id, r.title, r.isPrivate, r.host.id
        order by r.id desc
    """)
    List<RoomListItemResponse> findRoomsWithParticipantCount();

}
