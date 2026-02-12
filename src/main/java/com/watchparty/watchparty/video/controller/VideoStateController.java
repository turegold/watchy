package com.watchparty.watchparty.video.controller;

import com.watchparty.watchparty.common.response.ApiResponse;
import com.watchparty.watchparty.room.service.RoomService;
import com.watchparty.watchparty.video.dto.VideoControlRequest;
import com.watchparty.watchparty.video.dto.VideoStateResponse;
import com.watchparty.watchparty.video.service.VideoStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rooms/{roomId}/video")
@Slf4j
public class VideoStateController {

    private final VideoStateService videoStateService;
    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping
    public ResponseEntity<ApiResponse<VideoStateResponse>> getVideoState(@PathVariable Long roomId) {
        log.info("Get video state requested: roomId={}", roomId);
        VideoStateResponse state = videoStateService.getState(roomId);
        log.debug("Get video state completed: roomId={}, status={}, videoId={}, currentTime={}",
                roomId, state.getStatus(), state.getVideoId(), state.getCurrentTime());
        return ResponseEntity.ok(ApiResponse.ok("영상 상태 조회에 성공했습니다.", state));
    }

    @PostMapping("/control")
    public ResponseEntity<ApiResponse<Void>> controlVideo(
            @PathVariable Long roomId,
            @RequestParam Long userId,
            @RequestBody VideoControlRequest request
    ) {
        log.info("Control video requested: roomId={}, userId={}, action={}, currentTime={}, videoId={}",
                roomId, userId, request.getAction(), request.getCurrentTime(), request.getVideoId());

        roomService.validateHost(roomId, userId);
        videoStateService.applyControl(roomId, userId, request);

        VideoStateResponse state = videoStateService.getState(roomId);
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/video", state);

        log.info("Control video completed: roomId={}, status={}, videoId={}, currentTime={}, updatedBy={}",
                roomId, state.getStatus(), state.getVideoId(), state.getCurrentTime(), state.getUpdatedBy());

        return ResponseEntity.ok(ApiResponse.okMessage("영상 상태가 반영되었습니다."));
    }
}
