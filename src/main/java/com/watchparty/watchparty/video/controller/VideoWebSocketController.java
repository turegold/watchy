package com.watchparty.watchparty.video.controller;

import com.watchparty.watchparty.room.service.RoomService;
import com.watchparty.watchparty.video.dto.VideoControlRequest;
import com.watchparty.watchparty.video.dto.VideoStateResponse;
import com.watchparty.watchparty.video.service.VideoStateService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class VideoWebSocketController {

    private final VideoStateService videoStateService;
    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    // 영상 제어 (WebSocket)
    @MessageMapping("/video.control")
    public void controlVideo(VideoControlRequest request){

        Long roomId = request.getRoomId();
        Long userId = request.getUserId();

        log.info("WS control requested: roomId={}, userId={}, action={}, currentTime={}, videoId={}",
                roomId, userId, request.getAction(), request.getCurrentTime(), request.getVideoId());
        // 방장 검증
        roomService.validateHost(roomId, userId);

        // Redis 상태 변경
        videoStateService.applyControl(roomId, userId, request);

        // 변경된 상태 다시 조회
        VideoStateResponse state = videoStateService.getState(roomId);

        // 해당 방 사용자들에게 브로드캐스트
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + roomId + "/video",
                state
        );

        log.info("WS control completed: roomId={}, status={}, videoId={}, currentTime={}, updatedBy={}",
                roomId, state.getStatus(), state.getVideoId(), state.getCurrentTime(), state.getUpdatedBy());
    }
}
