package com.watchparty.watchparty.room.dto;

import lombok.Getter;

@Getter
public class RoomListItemResponse {

    private final Long roomId;
    private final String title;
    private final boolean isPrivate;

    private final Long hostUserId;
    private final long participantCount;

    public RoomListItemResponse(Long roomId,
                                String title,
                                boolean isPrivate,
                                Long hostUserId,
                                long participantCount) {
        this.roomId = roomId;
        this.title = title;
        this.isPrivate = isPrivate;
        this.hostUserId = hostUserId;
        this.participantCount = participantCount;
    }
}
