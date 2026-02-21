package com.watchparty.watchparty.chat.controller;



import com.watchparty.watchparty.chat.dto.ChatMessageResponse;
import com.watchparty.watchparty.chat.dto.ChatSendRequest;
import com.watchparty.watchparty.chat.service.ChatService;
import com.watchparty.watchparty.common.exception.AppException;
import com.watchparty.watchparty.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;

// STOMP 채팅 컨트롤러

@Controller
@RequiredArgsConstructor
public class ChatMessageController {

    private final String CHAT_TOPIC_PREFIX = "/topic/rooms/";

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;

    // 일반 채팅 메시지 전송
    @MessageMapping("/rooms/{roomId}/chat/send")
    public void sendChat(
            @DestinationVariable Long roomId,
            ChatSendRequest request,
            Principal principal
    ){
        Long userId = extractUserId(principal);

        // nickname 포함해서 payload 완성
        ChatMessageResponse payload = chatService.buildChatMessage(roomId, userId, request.getMessage());

        // 구독자에게 브로드캐스트
        messagingTemplate.convertAndSend(CHAT_TOPIC_PREFIX + roomId + "/chat", payload);


    }

    private Long extractUserId(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        try {
            return Long.parseLong(principal.getName());
        } catch (NumberFormatException e) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }
}
