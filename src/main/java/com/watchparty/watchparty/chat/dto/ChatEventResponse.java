package com.watchparty.watchparty.chat.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatEventResponse {

    // CHAT / SYSTEM
    private ChatEventKind kind;

    // 항상 포함하는 요소들
    private Long roomId;
    private LocalDateTime createdAt;

    // CHAT일 때만 사용
    private ChatMessageResponse chat;
    // SYSTEM일 때만 사용
    private ChatSystemMessageResponse system;

    public static ChatEventResponse chat(Long roomId, ChatMessageResponse chat){
        return ChatEventResponse.builder()
                .kind(ChatEventKind.CHAT)
                .roomId(roomId)
                .createdAt(chat.getCreatedAt())
                .chat(chat)
                .build();
    }

    public static ChatEventResponse system(Long roomId, ChatSystemMessageResponse system){
        return ChatEventResponse.builder()
                .kind(ChatEventKind.SYSTEM)
                .roomId(roomId)
                .createdAt(system.getCreatedAt())
                .system(system)
                .build();
    }

}
