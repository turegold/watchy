package com.watchparty.watchparty.room.controller;

import com.watchparty.watchparty.common.exception.AppException;
import com.watchparty.watchparty.common.exception.ErrorCode;
import com.watchparty.watchparty.common.response.ApiResponse;
import com.watchparty.watchparty.room.dto.CreateRoomRequest;
import com.watchparty.watchparty.room.dto.RoomListItemResponse;
import com.watchparty.watchparty.room.dto.RoomResponse;
import com.watchparty.watchparty.room.entity.Room;
import com.watchparty.watchparty.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rooms")
@Slf4j
public class RoomController {

    private final RoomService roomService;

    // 방 생성
    @PostMapping
    public ResponseEntity<ApiResponse<RoomResponse>> createRoom(
            Authentication authentication,
            @RequestBody CreateRoomRequest request
    ) {
        Long userId = extractUserId(authentication);
        log.info("Create room requested: userId={}, title={}, isPrivate={}", userId, request.getTitle(), request.isPrivate());
        Room room = roomService.createRoom(userId, request.getTitle(), request.isPrivate());
        log.info("Room created: roomId={}, hostUserId={}", room.getId(), userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("방이 생성되었습니다.", new RoomResponse(room)));
    }

    // 방 참여
    @PostMapping("/{roomId}/join")
    public ResponseEntity<ApiResponse<Void>> joinRoom(
            @PathVariable Long roomId,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        log.info("Join room requested: roomId={}, userId={}", roomId, userId);
        roomService.joinRoom(roomId, userId);
        log.info("Join room completed: roomId={}, userId={}", roomId, userId);
        return ResponseEntity.ok(ApiResponse.okMessage("방에 참여했습니다."));
    }

    // 방 나가기
    @PostMapping("/{roomId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveRoom(
            @PathVariable Long roomId,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        log.info("Leave room requested: roomId={}, userId={}", roomId, userId);
        roomService.leaveRoom(roomId, userId);
        log.info("Leave room completed: roomId={}, userId={}", roomId, userId);
        return ResponseEntity.ok(ApiResponse.okMessage("방에서 나갔습니다."));
    }

    // 방 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<RoomListItemResponse>>> getRooms() {
        log.info("Get rooms requested");
        List<RoomListItemResponse> response = roomService.getRooms();

        return ResponseEntity.ok(ApiResponse.ok("방 목록 조회에 성공했습니다.", response));
    }

    // Authentication에서 userId 추출
    private Long extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof Long) {
            return (Long) principal;
        }

        if (principal instanceof Integer) {
            return ((Integer) principal).longValue();
        }

        if (principal instanceof String) {
            try {
                return Long.parseLong((String) principal);
            } catch (NumberFormatException ignored) {
                // 아래에서 UNAUTHORIZED 처리
            }
        }

        throw new AppException(ErrorCode.UNAUTHORIZED);
    }
}
