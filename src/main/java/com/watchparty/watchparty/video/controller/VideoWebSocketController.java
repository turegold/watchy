package com.watchparty.watchparty.video.controller;

import com.watchparty.watchparty.room.service.RoomService;
import com.watchparty.watchparty.video.dto.VideoControlRequest;
import com.watchparty.watchparty.video.dto.VideoStateResponse;
import com.watchparty.watchparty.video.service.VideoStateService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class VideoWebSocketController {

    private final VideoStateService videoStateService;
    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    // 영상 제어 (WebSocket)
    @MessageMapping("/video.control")
    public void controlVideo(VideoControlRequest request){

        Long roomId = request.getRoomId();
        Long userId = request.getUserId();

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
    }
}
