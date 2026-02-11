package com.watchparty.watchparty.room.controller;

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

// 나중에 응답 DTO 만들어서 내려줘야 함

@RestController
@RequiredArgsConstructor
@RequestMapping("/rooms")
@Slf4j
public class RoomController {

    private final RoomService roomService;

    // 방 생성
    @PostMapping
    public ResponseEntity<Room> createRoom(
            @RequestParam Long userId,
            @RequestBody CreateRoomRequest request
            ){
        log.info("Create room requested: userId={}, title={}, isPrivate={}", userId, request.getTitle(), request.isPrivate());
        Room room = roomService.createRoom(
                userId,
                request.getTitle(),
                request.isPrivate()
        );
        log.info("Room created: roomId={}, hostUserId={}", room.getId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(room);
    }

    // 방 참여
    @PostMapping("/{roomId}/join")
    public ResponseEntity<Void> joinRoom(
            @PathVariable Long roomId,
            @RequestParam Long userId
    ){
        log.info("Join room requested: roomId={}, userId={}", roomId, userId);
        roomService.joinRoom(roomId, userId);
        log.info("Join room completed: roomId={}, userId={}", roomId, userId);
        return ResponseEntity.ok().build();
    }

    // 방 나가기
    @PostMapping("/{roomId}/leave")
    public ResponseEntity<Void> leaveRoom(
            @PathVariable Long roomId,
            @RequestParam Long userId
    ){
        log.info("Leave room requested: roomId={}, userId={}", roomId, userId);
        roomService.leaveRoom(roomId, userId);
        log.info("Leave room completed: roomId={}, userId={}", roomId, userId);
        return ResponseEntity.ok().build();
    }

    // 방 목록 조회
    @GetMapping
    public ResponseEntity<List<RoomResponse>> getRooms(){
        log.info("Get rooms requested");
        List<RoomResponse> response = roomService.getRooms().stream()
                .map(RoomResponse::new)
                .toList();

        log.info("Get rooms completed: count={}", response.size());
        return ResponseEntity.ok(response);
    }
}
