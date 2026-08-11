package com.watchparty.watchparty.chat.listener;

import com.watchparty.watchparty.chat.dto.ChatEventResponse;
import com.watchparty.watchparty.chat.dto.ChatSystemMessageResponse;
import com.watchparty.watchparty.chat.dto.SystemMessageType;
import com.watchparty.watchparty.user.dto.UserNickname;
import com.watchparty.watchparty.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
        Map<Long, String> nicknames = nicknamesOf(event.userId());
        String text = nicknames.getOrDefault(event.userId(), "Unkown") + "님이 입장했습니다.";

        broadcastSystem(event.roomId(), SystemMessageType.JOIN, text);
    }

    // 방 퇴장
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoomLeft(RoomLeftEvent event){
        Map<Long, String> nicknames = nicknamesOf(event.userId());
        String text = nicknames.getOrDefault(event.userId(), "Unkown") + "님이 퇴장했습니다.";

        broadcastSystem(event.roomId(), SystemMessageType.LEAVE, text);
    }

    // 방장 변경 — 예전엔 nicknameOf()를 2번 따로 호출해서 쿼리 2번 나갔음. 한 쿼리로 묶음.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onHostChanged(HostChangedEvent event){
        Map<Long, String> nicknames = nicknamesOf(event.oldHostUserId(), event.newHostUserId());
        String oldNick = nicknames.getOrDefault(event.oldHostUserId(), "Unkown");
        String newNick = nicknames.getOrDefault(event.newHostUserId(), "Unkown");

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

    // userId 1~2개를 쿼리 1번으로 조회(방장 변경 이벤트가 old/new 2명을 한 번에 필요로 함)
    private Map<Long, String> nicknamesOf(Long... userIds){
        List<Long> ids = Arrays.stream(userIds).filter(Objects::nonNull).distinct().toList();
        if(ids.isEmpty()){
            return Map.of();
        }
        return userProfileRepository.findNicknamesByUserIds(ids).stream()
                .collect(Collectors.toMap(UserNickname::userId, UserNickname::nickname));
    }
}
