package com.watchparty.watchparty.chat.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// 서버 -> 클라이언트 브로드캐스트용 채팅 메시지 DTO
@Getter
@Builder
public class ChatMessageResponse {

    // 방 ID
    private Long roomId;

    // 메시지 보낸 유저 정보
    private Long sendUserId;
    private String nickname;

    // 메시지 내용
    private String message;

    // 메시지 생성 시간
    private LocalDateTime createdAt;
}
