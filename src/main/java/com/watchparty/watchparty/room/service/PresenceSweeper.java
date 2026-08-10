package com.watchparty.watchparty.room.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/*
하트비트가 끊긴(유령) 참여자를 주기적으로 청소하는 스윕.
- 서버가 클라이언트에게 물어보지 않는다(수동적). 클라이언트가 보내온 마지막 하트비트 시각만 본다.
- HEARTBEAT_TIMEOUT_MILLIS(30초) 넘게 신호 없는 유저를 기존 leaveRoom 로직으로 강제 퇴장시킨다.
  → WS 정상 종료(SessionDisconnect)를 못 잡는 경우(절전/네트워크 블랙홀/강제종료)까지 확실히 커버하는 안전망.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceSweeper {

    private final RoomService roomService;

    // 10초마다: 각 방에서 30초 넘게 하트비트 없는 유저를 강제 퇴장
    @Scheduled(fixedDelayString = "${presence.sweep-interval-ms:10000}")
    public void sweep() {
        long cutoff = System.currentTimeMillis() - RoomService.HEARTBEAT_TIMEOUT_MILLIS;

        List<Long> roomIds = roomService.findAllRoomIds();
        for (Long roomId : roomIds) {
            List<Long> deadUserIds = roomService.findDeadParticipants(roomId, cutoff);
            for (Long userId : deadUserIds) {
                try {
                    roomService.leaveRoom(roomId, userId);
                    log.info("[sweep] 유령 참여자 정리: roomId={}, userId={}", roomId, userId);
                } catch (Exception e) {
                    // 한 명 정리 실패가 나머지를 막지 않도록
                    log.warn("[sweep] 정리 실패 roomId={}, userId={}", roomId, userId, e);
                }
            }
        }
    }
}
