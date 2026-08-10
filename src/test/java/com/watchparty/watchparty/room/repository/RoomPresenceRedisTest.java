package com.watchparty.watchparty.room.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 참여자 관리 DB→Redis 전환의 핵심(유령 청소 + 동시성 + 성능)을 실제 Redis에 붙여 검증.
 * 로컬 Redis(docker-compose up -d redis, localhost:6379)가 떠 있어야 실행됨.
 *
 * 여기서는 RoomService의 DB 부분(방 row 락/이벤트)은 제외하고, Redis 저장소 계층
 * (RoomParticipantRedisRepository + RoomHeartbeatRedisRepository)의 동작만 격리 검증한다.
 */
class RoomPresenceRedisTest {

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RoomParticipantRedisRepository participantRepo;
    private RoomHeartbeatRedisRepository heartbeatRepo;
    private Long roomId;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory("localhost", 6379);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        participantRepo = new RoomParticipantRedisRepository(redisTemplate);
        heartbeatRepo = new RoomHeartbeatRedisRepository(redisTemplate);
        roomId = System.nanoTime(); // 테스트마다 격리된 방
    }

    @AfterEach
    void tearDown() {
        participantRepo.deleteRoomParticipants(roomId);
        heartbeatRepo.delete(roomId);
        connectionFactory.destroy();
    }

    // 한 명이 방에 들어온 것으로 처리 (참여자 + 하트비트 기록)
    private void join(long userId, long heartbeatAt) {
        participantRepo.join(roomId, userId);
        heartbeatRepo.record(roomId, userId, heartbeatAt);
    }

    @Test
    @DisplayName("유령 청소: 하트비트가 끊긴 유저만 골라내고, 살아있는 유저는 남는다")
    void 유령_참여자_청소() {
        long now = System.currentTimeMillis();

        // 5명 입장 (초기 하트비트 = now)
        for (long userId = 1; userId <= 5; userId++) {
            join(userId, now);
        }

        // 1,2번은 계속 살아있음(하트비트 갱신), 3,4,5번은 "브라우저 닫힘"으로 하트비트 멈춤
        heartbeatRepo.record(roomId, 1L, now + 20_000);
        heartbeatRepo.record(roomId, 2L, now + 20_000);

        // 30초 경과 시점의 스윕: cutoff = (now+30s) - 30s = now
        long cutoff = (now + 30_000) - RoomHeartbeatCutoff.TIMEOUT;

        List<Long> dead = heartbeatRepo.findDeadUserIds(roomId, cutoff);

        // 마지막 하트비트가 now(=cutoff)인 3,4,5만 죽은 것으로 잡힘 (1,2는 now+20s라 살아있음)
        assertThat(dead).containsExactlyInAnyOrder(3L, 4L, 5L);

        // 스윕이 죽은 유저를 강제 퇴장(원자적 leave Lua)
        for (Long userId : dead) {
            participantRepo.leaveAndPickNextHost(roomId, userId);
            heartbeatRepo.remove(roomId, userId);
        }

        // 참여자 목록엔 살아있는 1,2만 남음
        assertThat(participantRepo.count(roomId)).isEqualTo(2L);
        assertThat(participantRepo.isParticipant(roomId, 1L)).isTrue();
        assertThat(participantRepo.isParticipant(roomId, 3L)).isFalse();
    }

    @Test
    @DisplayName("동시성: 여러 명이 동시에 나가도 인원수/방장 후보가 정확히 계산된다")
    void 동시_퇴장_원자성() throws InterruptedException {
        int userCount = 50;
        long now = System.currentTimeMillis();
        // 50명 입장 (입장 시각을 userId 순서로 벌려서 방장 후보 순서를 예측 가능하게)
        for (long userId = 1; userId <= userCount; userId++) {
            participantRepo.join(roomId, userId); // score=입장시각(자동)
        }
        assertThat(participantRepo.count(roomId)).isEqualTo(userCount);

        // 49명이 동시에 퇴장 (1번만 남김). 도착 순서는 스레드 스케줄링에 따라 뒤섞임.
        int leaving = userCount - 1;
        ExecutorService pool = Executors.newFixedThreadPool(leaving);
        CountDownLatch ready = new CountDownLatch(leaving);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(leaving);

        for (long userId = 2; userId <= userCount; userId++) {
            long uid = userId;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    participantRepo.leaveAndPickNextHost(roomId, uid);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        // 동시에 몰려 나가도 정확히 1명만 남음 (인원수 유실/중복 없음)
        assertThat(participantRepo.count(roomId)).isEqualTo(1L);
        assertThat(participantRepo.isParticipant(roomId, 1L)).isTrue();
    }

    @Test
    @DisplayName("동시성: 마지막 한 명 퇴장은 정확히 한 번만 '0명'을 관측한다(빈 방 중복 삭제 방지)")
    void 마지막_퇴장은_한_번만_0명() throws InterruptedException {
        int userCount = 30;
        for (long userId = 1; userId <= userCount; userId++) {
            participantRepo.join(roomId, userId);
        }

        ExecutorService pool = Executors.newFixedThreadPool(userCount);
        CountDownLatch ready = new CountDownLatch(userCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(userCount);
        AtomicInteger sawZero = new AtomicInteger(0);

        for (long userId = 1; userId <= userCount; userId++) {
            long uid = userId;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    RoomParticipantRedisRepository.LeaveResult r =
                            participantRepo.leaveAndPickNextHost(roomId, uid);
                    if (r.remainingCount() == 0L) {
                        sawZero.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        // 30명이 동시에 나가도 "내가 마지막(0명)이다"를 관측한 스레드는 정확히 1개
        // → 빈 방 삭제 로직이 딱 한 번만 실행됨이 보장된다
        assertThat(sawZero.get()).isEqualTo(1);
        assertThat(participantRepo.count(roomId)).isEqualTo(0L);
    }

    @Test
    @DisplayName("성능: Redis 참여자 join+leave 1회 왕복 지연 측정")
    void 성능_join_leave_지연_측정() {
        int warmup = 200;
        int iterations = 2000;

        // 워밍업 (JIT/커넥션)
        for (int i = 0; i < warmup; i++) {
            participantRepo.join(roomId, 1L);
            participantRepo.leaveAndPickNextHost(roomId, 1L);
        }

        long startNs = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            participantRepo.join(roomId, 1L);
            heartbeatRepo.record(roomId, 1L, System.currentTimeMillis());
            participantRepo.leaveAndPickNextHost(roomId, 1L);
            heartbeatRepo.remove(roomId, 1L);
        }
        long elapsedNs = System.nanoTime() - startNs;

        double avgMicros = (elapsedNs / 1000.0) / iterations;
        double opsPerSec = iterations / (elapsedNs / 1_000_000_000.0);

        System.out.printf(
                "[PERF] Redis 참여자 join+leave 사이클 %d회: 평균 %.1fµs/cycle, 약 %.0f cycle/s%n",
                iterations, avgMicros, opsPerSec);

        // 회귀 방지용 느슨한 상한 (로컬 Redis 기준 넉넉히). 값 자체보다 콘솔 출력이 목적.
        assertThat(avgMicros).isLessThan(5000.0);
    }

    // 테스트에서 RoomService 상수를 그대로 참조하기 위한 헬퍼
    private static final class RoomHeartbeatCutoff {
        static final long TIMEOUT = com.watchparty.watchparty.room.service.RoomService.HEARTBEAT_TIMEOUT_MILLIS;
    }
}
