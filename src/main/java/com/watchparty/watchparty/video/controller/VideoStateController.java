package com.watchparty.watchparty.video.controller;

import com.watchparty.watchparty.room.service.RoomService;
import com.watchparty.watchparty.video.dto.VideoControlRequest;
import com.watchparty.watchparty.video.dto.VideoStateResponse;
import com.watchparty.watchparty.video.service.VideoStateService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    // 현재 영상 상태 조회
    @GetMapping
    public ResponseEntity<VideoStateResponse> getVideoState(
            @PathVariable Long roomId
    ){
        log.info("Get video state requested: roomId={}", roomId);
        VideoStateResponse state = videoStateService.getState(roomId);
        log.debug("Get video state completed: roomId={}, status={}, videoId={}, currentTime={}",
                roomId, state.getStatus(), state.getVideoId(), state.getCurrentTime());
        return ResponseEntity.ok(state);
    }

    // 영상 제어 (방장만 가능)
    @PostMapping("/control")
    public ResponseEntity<Void> controlVideo(
            @PathVariable Long roomId,
            @RequestParam Long userId,
            @RequestBody VideoControlRequest request
            ){
        log.info("Control video requested: roomId={}, userId={}, action={}, currentTime={}, videoId={}",
                roomId, userId, request.getAction(), request.getCurrentTime(), request.getVideoId());
        // 방장 검증
        roomService.validateHost(roomId, userId);

        // 영상 상태 변경
        videoStateService.applyControl(roomId, userId, request);

        // 변경된 상태 조회
        VideoStateResponse state = videoStateService.getState(roomId);

        // 브로드캐스트
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + roomId + "/video",
                state
        );

        log.info("Control video completed: roomId={}, status={}, videoId={}, currentTime={}, updatedBy={}",
                roomId, state.getStatus(), state.getVideoId(), state.getCurrentTime(), state.getUpdatedBy());
        return ResponseEntity.ok().build();
    }
}
