package com.watchparty.watchparty.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 클라이언트 -> 서버(STOMP)로 보내는 채팅 메시지 DTO
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatSendRequest {

    // 사용자가 입력한 채팅 내용
    @NotBlank(message = "메세지는 비어있을 수 없습니다.")
    @Size(max = 500, message = "메시지는 최대 500자까지 입력 가능합니다.")
    private String message;
}
