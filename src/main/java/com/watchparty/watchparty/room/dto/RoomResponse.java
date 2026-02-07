package com.watchparty.watchparty.room.dto;

import com.watchparty.watchparty.room.entity.Room;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class RoomResponse {
    private Long roomId;
    private String title;
    private boolean isPrivate;

    private Long hostId;
    private String hostEmail;

    private LocalDateTime createdAt;

    public RoomResponse(Room room) {
        this.roomId = room.getId();
        this.title = room.getTitle();
        this.isPrivate = room.isPrivate();
        this.createdAt = room.getCreatedAt();

        this.hostId = room.getHost().getId();
        this.hostEmail = room.getHost().getEmail();
    }
}
