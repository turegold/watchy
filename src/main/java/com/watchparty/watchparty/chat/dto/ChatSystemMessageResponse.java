package com.watchparty.watchparty.chat.dto;

// 서버 이벤트용 시스템 메시지 DTO

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatSystemMessageResponse {

    // 시스템 메시지 타입
    private SystemMessageType type;

    // 화면에 표시할 메시지
    private String message;

    // 이벤트 발생 시간
    private LocalDateTime createdAt;
}
