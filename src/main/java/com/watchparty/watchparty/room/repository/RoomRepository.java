package com.watchparty.watchparty.room.repository;

import com.watchparty.watchparty.room.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {
}
