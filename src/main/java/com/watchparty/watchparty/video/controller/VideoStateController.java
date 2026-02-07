package com.watchparty.watchparty.video.controller;

import com.watchparty.watchparty.room.service.RoomService;
import com.watchparty.watchparty.video.dto.VideoControlRequest;
import com.watchparty.watchparty.video.dto.VideoStateResponse;
import com.watchparty.watchparty.video.service.VideoStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rooms/{roomId}/video")
public class VideoStateController {

    private final VideoStateService videoStateService;
    private final RoomService roomService;

    // 현재 영상 상태 조회
    @GetMapping
    public ResponseEntity<VideoStateResponse> getVideoState(
            @PathVariable Long roomId
    ){
        VideoStateResponse state = videoStateService.getState(roomId);
        return ResponseEntity.ok(state);
    }

    // 영상 제어 (방장만 가능)
    @PostMapping("/control")
    public ResponseEntity<Void> controlVideo(
            @PathVariable Long roomId,
            @RequestParam Long userId,
            @RequestBody VideoControlRequest request
            ){
        // 방장 검증
        roomService.validateHost(roomId, userId);

        // 영상 상태 변경
        videoStateService.applyControl(roomId, userId, request);

        return ResponseEntity.ok().build();
    }
}
