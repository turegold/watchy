package com.watchparty.watchparty.room.service;

import com.watchparty.watchparty.room.entity.Room;
import com.watchparty.watchparty.room.entity.RoomMember;
import com.watchparty.watchparty.room.repository.RoomMemberRepository;
import com.watchparty.watchparty.room.repository.RoomRepository;
import com.watchparty.watchparty.user.entity.User;
import com.watchparty.watchparty.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @InjectMocks
    private RoomService roomService;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    void 방_생성_성공() {
        //given
        User host = new User("test@test.com");
        Room room = new Room("테스트 방", host, false);

        when(userRepository.findById(1L)).thenReturn(Optional.of(host));
        when(roomRepository.save(any(Room.class))).thenReturn(room);

        //when
        Room result = roomService.createRoom(1L, "테스트 방", false);

        //then
        assertThat(result.getTitle()).isEqualTo("테스트 방");
        verify(roomMemberRepository).save(any(RoomMember.class));

    }

    @Test
    void 방_참여_성공() {
        //given
        User host = new User("host@test.com");
        User user = new User("user@test.com");
        Room room = new Room("방", host, false);

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(roomMemberRepository.existsByRoomAndUser(room, user)).thenReturn(false);

        //when
        roomService.joinRoom(1L, 2L);

        //then
        verify(roomMemberRepository).save(any(RoomMember.class));
    }

    @Test
    void 이미_참여한_유저는_예외() {
        // given
        User user = new User("user@test.com");
        Room room = new Room("방", user, false);

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(roomMemberRepository.existsByRoomAndUser(room, user)).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> roomService.joinRoom(1L, 2L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 방장이_나가면_방장_위임() {
        // given
        User host = new User("host@test.com");
        User next = new User("next@test.com");
        Room room = new Room("방", host, false);

        RoomMember hostMember = new RoomMember(room, host);
        RoomMember nextMember = new RoomMember(room, next);

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(userRepository.findById(1L)).thenReturn(Optional.of(host));
        when(roomMemberRepository.findByRoomAndUser(room, host))
                .thenReturn(Optional.of(hostMember));
        when(roomMemberRepository.findByRoom(room))
                .thenReturn(List.of(nextMember));

        // when
        roomService.leaveRoom(1L, 1L);

        // then
        assertThat(room.getHost()).isEqualTo(next);
    }

    @Test
    void 마지막_유저가_나가면_방_삭제() {
        // given
        User host = new User("host@test.com");
        Room room = new Room("방", host, false);
        RoomMember member = new RoomMember(room, host);

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(userRepository.findById(1L)).thenReturn(Optional.of(host));
        when(roomMemberRepository.findByRoomAndUser(room, host))
                .thenReturn(Optional.of(member));
        when(roomMemberRepository.findByRoom(room))
                .thenReturn(List.of());

        // when
        roomService.leaveRoom(1L, 1L);

        // then
        verify(roomRepository).delete(room);
    }



}