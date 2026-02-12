package com.watchparty.watchparty.room.controller;

import com.watchparty.watchparty.common.response.ApiResponse;
import com.watchparty.watchparty.room.dto.CreateRoomRequest;
import com.watchparty.watchparty.room.dto.RoomResponse;
import com.watchparty.watchparty.room.entity.Room;
import com.watchparty.watchparty.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rooms")
@Slf4j
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    public ResponseEntity<ApiResponse<RoomResponse>> createRoom(
            @RequestParam Long userId,
            @RequestBody CreateRoomRequest request
    ) {
        log.info("Create room requested: userId={}, title={}, isPrivate={}", userId, request.getTitle(), request.isPrivate());
        Room room = roomService.createRoom(userId, request.getTitle(), request.isPrivate());
        log.info("Room created: roomId={}, hostUserId={}", room.getId(), userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("방이 생성되었습니다.", new RoomResponse(room)));
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<ApiResponse<Void>> joinRoom(
            @PathVariable Long roomId,
            @RequestParam Long userId
    ) {
        log.info("Join room requested: roomId={}, userId={}", roomId, userId);
        roomService.joinRoom(roomId, userId);
        log.info("Join room completed: roomId={}, userId={}", roomId, userId);
        return ResponseEntity.ok(ApiResponse.okMessage("방에 참여했습니다."));
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveRoom(
            @PathVariable Long roomId,
            @RequestParam Long userId
    ) {
        log.info("Leave room requested: roomId={}, userId={}", roomId, userId);
        roomService.leaveRoom(roomId, userId);
        log.info("Leave room completed: roomId={}, userId={}", roomId, userId);
        return ResponseEntity.ok(ApiResponse.okMessage("방에서 나갔습니다."));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoomResponse>>> getRooms() {
        log.info("Get rooms requested");
        List<RoomResponse> response = roomService.getRooms().stream()
                .map(RoomResponse::new)
                .toList();

        log.info("Get rooms completed: count={}", response.size());
        return ResponseEntity.ok(ApiResponse.ok("방 목록 조회에 성공했습니다.", response));
    }
}
