package com.watchparty.watchparty.room.repository;

import com.watchparty.watchparty.room.dto.RoomListItemResponse;
import com.watchparty.watchparty.room.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {

    // 방 목록(참여자 수는 Redis에서 채우므로 0으로 두고, 서비스에서 ZCARD로 채운다)
    // 방장 프로필 이미지도 여기서 같은 join으로 raw key까지 가져와 N+1(방마다 프로필 재조회)을 없앤다.
    @Query("""
        select new com.watchparty.watchparty.room.dto.RoomListItemResponse(
            r.id,
            r.title,
            r.isPrivate,
            r.host.id,
            up.nickname,
            0L,
            up.profileImageKey
        )
        from Room r
        left join UserProfile up on up.user = r.host
        order by r.id desc
    """)
    List<RoomListItemResponse> findRoomsForList();

    // 스윕(하트비트 만료 청소)이 순회할 전체 방 id
    @Query("select r.id from Room r")
    List<Long> findAllRoomIds();

}
