package com.watchparty.watchparty.chat.listener;

import com.watchparty.watchparty.chat.dto.ChatEventResponse;
import com.watchparty.watchparty.chat.dto.ChatSystemMessageResponse;
import com.watchparty.watchparty.chat.dto.SystemMessageType;
import com.watchparty.watchparty.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.startup.HostConfig;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/*
Room 도메인 이벤트를 받아서,
AFTER_COMMIT 시점에 채팅 토픽(/topic/rooms/{roomId}/chat)으로 시스템 메시지를 브로드캐스트
 */

@Component
@RequiredArgsConstructor
@Slf4j
public class RoomSystemMessageListener {

    private static final String CHAT_TOPIC_PREFIX = "/topic/rooms/";

    private final SimpMessagingTemplate messagingTemplate;
    private final UserProfileRepository userProfileRepository;

    // Events (room의 Service가 발생)
    public record RoomJoinedEvent(Long roomId, Long userId){}
    public record RoomLeftEvent(Long roomId, Long userId){}
    public record HostChangedEvent(Long roomId, Long oldHostUserId, Long newHostUserId){}

    // Listeners (커밋 후 발송)

    // 방 입장
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoomJoined(RoomJoinedEvent event){
        String nickname = nicknameOf(event.userId());
        String text = nickname + "님이 입장했습니다.";

        broadcastSystem(event.roomId(), SystemMessageType.JOIN, text);
    }

    // 방 퇴장
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoomLeft(RoomLeftEvent event){
        String nickname = nicknameOf(event.userId());
        String text = nickname + "님이 퇴장했습니다.";

        broadcastSystem(event.roomId(), SystemMessageType.LEAVE, text);
    }

    // 방장 변경
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onHostChanged(HostChangedEvent event){
        String oldNick = nicknameOf(event.oldHostUserId());
        String newNick = nicknameOf(event.newHostUserId());

        String text = "방장이 " + oldNick + "님에서 " + newNick + "님으로 변경되었습니다.";

        broadcastSystem(event.roomId(), SystemMessageType.HOST_CHANGE, text);
    }
    // Helper
    private void broadcastSystem(Long roomId, SystemMessageType type, String message){
        ChatSystemMessageResponse system = ChatSystemMessageResponse.builder()
                .type(type)
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();

        ChatEventResponse payload = ChatEventResponse.system(roomId,system);

        String topic = CHAT_TOPIC_PREFIX + roomId + "/chat";
        messagingTemplate.convertAndSend(topic, payload);

        log.info("[system-chat] roomId={}, type={}, message={}", roomId, type, message);
    }

    private String nicknameOf(Long userId){
        if(userId == null){
            return "Unkown";
        }
        return userProfileRepository.findNicknameByUserId(userId).orElse("Unkown");
    }
}
