package com.watchparty.watchparty.room.service;

import com.watchparty.watchparty.common.exception.AppException;
import com.watchparty.watchparty.room.entity.Room;
import com.watchparty.watchparty.room.repository.RoomHeartbeatRedisRepository;
import com.watchparty.watchparty.room.repository.RoomParticipantRedisRepository;
import com.watchparty.watchparty.room.repository.RoomRepository;
import com.watchparty.watchparty.user.entity.User;
import com.watchparty.watchparty.user.repository.UserRepository;
import com.watchparty.watchparty.video.service.VideoStateService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * RoomService 단위 테스트 (참여자 관리 DB→Redis 전환 이후 버전).
 * Redis/DB 의존성은 모두 mock — 서비스의 분기 로직(방장 위임/빈 방 삭제/멱등 leave)만 검증한다.
 * 실제 Redis 원자성·유령 청소는 RoomPresenceRedisTest(실 Redis)에서 검증.
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @InjectMocks
    private RoomService roomService;

    @Mock private RoomRepository roomRepository;
    @Mock private UserRepository userRepository;
    @Mock private EntityManager entityManager;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private VideoStateService videoStateService;
    @Mock private RoomParticipantRedisRepository participantRedis;
    @Mock private RoomHeartbeatRedisRepository heartbeatRedis;

    private Room roomWithId(Long id, User host) {
        Room room = new Room("방", host, false);
        ReflectionTestUtils.setField(room, "id", id);
        return room;
    }

    @Test
    void 방_생성시_방장을_참여자와_하트비트에_등록() {
        User host = User.referenceById(1L);
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(host));
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> {
            Room r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 10L);
            return r;
        });

        Room result = roomService.createRoom(1L, "테스트 방", false);

        assertThat(result.getTitle()).isEqualTo("테스트 방");
        verify(participantRedis).join(10L, 1L);
        verify(heartbeatRedis).record(eq(10L), eq(1L), anyLong());
    }

    @Test
    void 방_참여_성공() {
        User user = User.referenceById(2L);
        Room room = roomWithId(1L, User.referenceById(1L));
        when(entityManager.find(eq(Room.class), eq(1L), eq(LockModeType.PESSIMISTIC_WRITE)))
                .thenReturn(room);
        when(userRepository.findById(2L)).thenReturn(java.util.Optional.of(user));
        when(participantRedis.isParticipant(1L, 2L)).thenReturn(false);

        roomService.joinRoom(1L, 2L);

        verify(participantRedis).join(1L, 2L);
        verify(heartbeatRedis).record(eq(1L), eq(2L), anyLong());
        // publishEvent(Object)/publishEvent(ApplicationEvent) 오버로드 중 실제 호출은 Object 쪽
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void 이미_참여한_유저는_예외() {
        Room room = roomWithId(1L, User.referenceById(1L));
        when(entityManager.find(eq(Room.class), eq(1L), eq(LockModeType.PESSIMISTIC_WRITE)))
                .thenReturn(room);
        when(userRepository.findById(2L)).thenReturn(java.util.Optional.of(User.referenceById(2L)));
        when(participantRedis.isParticipant(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> roomService.joinRoom(1L, 2L))
                .isInstanceOf(AppException.class);
        verify(participantRedis, never()).join(anyLong(), anyLong());
    }

    @Test
    void 없는_방에_참여하면_예외() {
        when(entityManager.find(eq(Room.class), eq(99L), eq(LockModeType.PESSIMISTIC_WRITE)))
                .thenReturn(null);

        assertThatThrownBy(() -> roomService.joinRoom(99L, 2L))
                .isInstanceOf(AppException.class);
    }

    @Test
    void 방장이_나가면_다음_방장에게_위임() {
        User host = User.referenceById(1L);
        Room room = roomWithId(1L, host);
        when(entityManager.find(eq(Room.class), eq(1L), eq(LockModeType.PESSIMISTIC_WRITE)))
                .thenReturn(room);
        when(participantRedis.isParticipant(1L, 1L)).thenReturn(true);
        when(participantRedis.leaveAndPickNextHost(1L, 1L))
                .thenReturn(new RoomParticipantRedisRepository.LeaveResult(1L, 2L));
        User nextHost = User.referenceById(2L);
        when(userRepository.getReferenceById(2L)).thenReturn(nextHost);

        roomService.leaveRoom(1L, 1L);

        assertThat(room.getHost().getId()).isEqualTo(2L);
        verify(heartbeatRedis).remove(1L, 1L);
        verify(roomRepository, never()).delete(any());
    }

    @Test
    void 마지막_유저가_나가면_방과_리소스_삭제() {
        User host = User.referenceById(1L);
        Room room = roomWithId(1L, host);
        when(entityManager.find(eq(Room.class), eq(1L), eq(LockModeType.PESSIMISTIC_WRITE)))
                .thenReturn(room);
        when(participantRedis.isParticipant(1L, 1L)).thenReturn(true);
        when(participantRedis.leaveAndPickNextHost(1L, 1L))
                .thenReturn(new RoomParticipantRedisRepository.LeaveResult(0L, null));

        roomService.leaveRoom(1L, 1L);

        verify(videoStateService).deleteState(1L);
        verify(participantRedis).deleteRoomParticipants(1L);
        verify(heartbeatRedis).delete(1L);
        verify(roomRepository).delete(room);
    }

    @Test
    void 이미_나간_유저의_leave는_멱등하게_무시() {
        Room room = roomWithId(1L, User.referenceById(1L));
        when(entityManager.find(eq(Room.class), eq(1L), eq(LockModeType.PESSIMISTIC_WRITE)))
                .thenReturn(room);
        when(participantRedis.isParticipant(1L, 5L)).thenReturn(false);

        roomService.leaveRoom(1L, 5L);

        // 참여자가 아니면 하트비트만 정리하고, 실제 퇴장/삭제 로직은 타지 않는다
        verify(heartbeatRedis).remove(1L, 5L);
        verify(participantRedis, never()).leaveAndPickNextHost(anyLong(), anyLong());
        verify(roomRepository, never()).delete(any());
    }

    @Test
    void 이미_삭제된_방의_leave는_안전하게_무시() {
        when(entityManager.find(eq(Room.class), eq(1L), eq(LockModeType.PESSIMISTIC_WRITE)))
                .thenReturn(null);

        roomService.leaveRoom(1L, 5L);

        verify(heartbeatRedis).remove(1L, 5L);
        verify(participantRedis, never()).leaveAndPickNextHost(anyLong(), anyLong());
    }
}
