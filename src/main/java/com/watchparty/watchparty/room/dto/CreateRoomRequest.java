package com.watchparty.watchparty.room.dto;

import lombok.Getter;

@Getter
public class CreateRoomRequest {

    private String title;
    private boolean isPrivate;
}
