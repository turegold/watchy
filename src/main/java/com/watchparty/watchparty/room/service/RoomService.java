package com.watchparty.watchparty.room.service;

import com.watchparty.watchparty.room.entity.Room;
import com.watchparty.watchparty.room.entity.RoomMember;
import com.watchparty.watchparty.room.repository.RoomMemberRepository;
import com.watchparty.watchparty.room.repository.RoomRepository;
import com.watchparty.watchparty.user.entity.User;
import com.watchparty.watchparty.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;

    // 방 생성
    public Room createRoom(Long hostUserId, String title, boolean isPrivate){
        User host = userRepository.findById(hostUserId)
                .orElseThrow(()-> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        Room room = new Room(title, host, isPrivate);
        Room savedRoom = roomRepository.save(room);

        // 방장은 자동으로 방 참여자로 등록
        RoomMember hostMember = new RoomMember(savedRoom, host);
        roomMemberRepository.save(hostMember);

        return savedRoom;
    }

    // 방 참여
    public void joinRoom(Long roomId, Long userId){
        Room room = roomRepository.findById(roomId)
                .orElseThrow(()-> new IllegalArgumentException("존재하지 않는 방입니다."));
        User user = userRepository.findById(userId)
                .orElseThrow(()-> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        if(roomMemberRepository.existsByRoomAndUser(room, user)){
            throw new IllegalStateException("이미 방에 참여한 유저입니다.");
        }

        RoomMember roomMember = new RoomMember(room, user);
        roomMemberRepository.save(roomMember);
    }

    // 방 나가기
    @Transactional
    public void leaveRoom(Long roomId, Long userId){
        Room room = roomRepository.findById(roomId)
                .orElseThrow(()-> new IllegalArgumentException("존재하지 않는 방입니다."));
        User user = userRepository.findById(userId)
                .orElseThrow(()-> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        RoomMember member = roomMemberRepository.findByRoomAndUser(room, user)
                .orElseThrow(()->new IllegalStateException("방에 없는 사용자입니다."));

        roomMemberRepository.delete(member);

        List<RoomMember> remaining = roomMemberRepository.findByRoom(room);

        // 방에 아무도 없으면 방 삭제
        if(remaining.isEmpty()){
            roomRepository.delete(room);
            return;
        }

        // 방장이 나갔으면 방장 위임
        if(room.getHost().equals(user)){
            RoomMember nextHost = remaining.get(0);
            room.changeHost(nextHost.getUser());
        }
    }

    // 방 목록 조회
    @Transactional(readOnly = true)
    public List<Room> getRooms(){
        return roomRepository.findAll();
    }
}
