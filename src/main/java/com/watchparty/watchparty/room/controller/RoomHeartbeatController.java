package com.watchparty.watchparty.room.controller;

import com.watchparty.watchparty.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/*
클라이언트가 방에 있는 동안 주기적으로 보내는 "저 살아있어요" 신호를 받아
Redis 하트비트 ZSET의 시각을 갱신한다. (서버가 ping을 보내는 게 아니라 클라이언트가 스스로 알림)
신호가 끊기면 스케줄러(PresenceSweeper)가 해당 유저를 강제 퇴장시킨다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class RoomHeartbeatController {

    private final RoomService roomService;

    @MessageMapping("/rooms/{roomId}/heartbeat")
    public void heartbeat(@DestinationVariable Long roomId, Principal principal) {
        if (principal == null || principal.getName() == null) {
            // 비인증 하트비트는 무시 (연결은 통과시키되 여기서 걸러짐)
            return;
        }
        Long userId;
        try {
            userId = Long.parseLong(principal.getName());
        } catch (NumberFormatException e) {
            return;
        }
        roomService.handleHeartbeat(roomId, userId);
    }
}
