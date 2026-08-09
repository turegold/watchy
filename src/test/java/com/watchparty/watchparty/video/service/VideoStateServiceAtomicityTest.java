package com.watchparty.watchparty.video.service;

import com.watchparty.watchparty.video.dto.VideoActionType;
import com.watchparty.watchparty.video.dto.VideoControlRequest;
import com.watchparty.watchparty.video.dto.VideoStateResponse;
import com.watchparty.watchparty.video.redis.VideoRedisKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * VideoStateService.applyControl의 Lua CAS 원자성 검증.
 * 로컬 Redis(docker-compose up -d redis, localhost:6379)가 떠 있어야 실행됨 — mock이 아니라 실제 Redis에 붙어서 검증.
 */
class VideoStateServiceAtomicityTest {

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private VideoStateService videoStateService;
    private Long roomId;
    private String videoKey;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory("localhost", 6379);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        videoStateService = new VideoStateService(redisTemplate);
        roomId = System.nanoTime(); // 테스트마다 격리된 room 키 사용
        videoKey = VideoRedisKeys.videoKey(roomId);

        redisTemplate.delete(videoKey);
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete(videoKey);
        redisTemplate.delete(videoKey + ":legacy-demo");
        connectionFactory.destroy();
    }

    @Test
    void 늦게_도착한_과거_액션은_무시되고_상태를_바꾸지_못한다() {
        // given: 클릭 순서는 PLAY(t=1000) 먼저 → PAUSE(t=2000) 나중
        // 하지만 네트워크 지연으로 서버엔 PAUSE가 먼저 도착하고, PLAY가 뒤늦게 도착
        VideoControlRequest pauseArrivesFirst = controlRequest(VideoActionType.PAUSE, 50.0, 2000L);
        VideoControlRequest playArrivesLate = controlRequest(VideoActionType.PLAY, 10.0, 1000L);

        // when
        boolean pauseApplied = videoStateService.applyControl(roomId, 1L, pauseArrivesFirst);
        boolean playApplied = videoStateService.applyControl(roomId, 2L, playArrivesLate);

        // then: 늦게 도착한 PLAY(더 과거 시각)는 반영되지 않고, PAUSE가 최종 상태로 남는다
        assertThat(pauseApplied).isTrue();
        assertThat(playApplied).isFalse();

        VideoStateResponse state = videoStateService.getState(roomId);
        assertThat(state.getStatus()).isEqualTo(VideoRedisKeys.STATUS_PAUSED);
        assertThat(state.getUpdatedBy()).isEqualTo(1L); // PLAY(userId=2)가 아니라 PAUSE(userId=1)가 유지됨
    }

    @Test
    void 정상_순서로_도착하면_그대로_반영된다() {
        VideoControlRequest play = controlRequest(VideoActionType.PLAY, 10.0, 1000L);
        VideoControlRequest pause = controlRequest(VideoActionType.PAUSE, 20.0, 2000L);

        boolean playApplied = videoStateService.applyControl(roomId, 1L, play);
        boolean pauseApplied = videoStateService.applyControl(roomId, 1L, pause);

        assertThat(playApplied).isTrue();
        assertThat(pauseApplied).isTrue();
        assertThat(videoStateService.getState(roomId).getStatus()).isEqualTo(VideoRedisKeys.STATUS_PAUSED);
    }

    @Test
    void 동시에_요청이_몰려도_가장_최신_액션만_최종_반영된다() throws InterruptedException {
        // given: threadCount개의 스레드가 서로 다른 clientActionTime(0..threadCount-1)으로
        // 동시에 SEEK를 보낸다. 실제 Redis 도착 순서는 스레드 스케줄링에 따라 뒤섞인다.
        int threadCount = 30;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            long clientTs = i;
            long userId = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    VideoControlRequest req = controlRequest(VideoActionType.SEEK, (double) clientTs, clientTs);
                    videoStateService.applyControl(roomId, userId, req);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown(); // 전부 동시에 출발
        boolean finished = done.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        // then: 실제 도착 순서(=스케줄링 결과)와 무관하게, clientActionTime이 가장 큰(threadCount-1) 액션만 남는다
        assertThat(finished).isTrue();
        VideoStateResponse state = videoStateService.getState(roomId);
        assertThat(state.getUpdatedBy()).isEqualTo((long) (threadCount - 1));
        assertThat(state.getCurrentTime()).isEqualTo((double) (threadCount - 1));
    }

    @Test
    void 구버전_방식_개별_HSET_여러번은_torn_read가_실제로_재현된다() throws InterruptedException {
        // 예전 applyControl이 하던 방식 그대로 재현: 필드 4개를 순서대로 개별 HSET.
        // (프로덕션 코드는 이미 고쳤으므로, 여기서는 "고치기 전엔 왜 문제였는지"를 별도로 시뮬레이션)
        String legacyKey = videoKey + ":legacy-demo";
        AtomicBoolean tornDetected = new AtomicBoolean(false);
        AtomicBoolean stop = new AtomicBoolean(false);

        AtomicInteger printedCount = new AtomicInteger(0);
        HashOperations<String, String, String> readerOps = redisTemplate.opsForHash();
        Thread reader = new Thread(() -> {
            while (!stop.get()) {
                Map<String, String> snapshot = readerOps.entries(legacyKey);
                if (!snapshot.isEmpty() && !allFieldsShareSameTag(snapshot)) {
                    tornDetected.set(true);
                    // 실제로 뭐가 섞였는지 눈으로 보이게 최대 5번만 출력 (매 while 루프마다 찍으면 수백 줄 쏟아짐)
                    if (printedCount.incrementAndGet() <= 5) {
                        System.out.println("[TORN READ #" + printedCount.get() + "] " + snapshot);
                    }
                }
            }
        });
        reader.start();

        // 라운드마다 새 태그로 4개 필드를 순서대로(사이 지연 포함) 씀 — 리더가 그 사이를 찍으면 필드끼리 태그가 섞여 보임
        for (int round = 0; round < 30; round++) {
            legacyNonAtomicWrite(legacyKey, "TAG-" + round);
        }

        stop.set(true);
        reader.join(2000);

        assertThat(tornDetected)
                .as("개별 HSET 여러 번 방식은 쓰는 도중 읽으면 필드끼리 다른 태그가 섞인 torn read를 재현해야 함")
                .isTrue();
    }

    @Test
    void Lua_스크립트로_묶으면_같은_조건에서_torn_read가_재현되지_않는다() throws InterruptedException {
        // 실제 프로덕션 경로(videoStateService.applyControl) 그대로 사용.
        // baseTime과 updatedBy를 같은 값(round)으로 실어 보내고, 매 스냅샷에서 이 둘이 항상 일치하는지 확인.
        AtomicBoolean tornDetected = new AtomicBoolean(false);
        AtomicBoolean stop = new AtomicBoolean(false);

        Thread reader = new Thread(() -> {
            while (!stop.get()) {
                VideoStateResponse state = videoStateService.getState(roomId);
                if (state.getUpdatedBy() != null
                        && state.getCurrentTime() != state.getUpdatedBy().doubleValue()) {
                    tornDetected.set(true);
                }
            }
        });
        reader.start();

        // 구버전 테스트와 같은 횟수만큼 "묶어서" 갱신. Lua라 지연을 넣을 수조차 없음 — 그게 핵심.
        for (int round = 0; round < 3000; round++) {
            VideoControlRequest req = controlRequest(VideoActionType.SEEK, (double) round, (long) round);
            videoStateService.applyControl(roomId, (long) round, req);
        }

        stop.set(true);
        reader.join(2000);

        assertThat(tornDetected)
                .as("Lua로 필드들을 한 번에 묶으면 baseTime/updatedBy가 항상 같은 라운드 값으로 일치해야 함(torn read 불가능)")
                .isFalse();
    }

    private void legacyNonAtomicWrite(String key, String tag) {
        HashOperations<String, String, String> ops = redisTemplate.opsForHash();
        ops.put(key, "f1", tag);
        sleepQuietly();
        ops.put(key, "f2", tag);
        sleepQuietly();
        ops.put(key, "f3", tag);
        sleepQuietly();
        ops.put(key, "f4", tag);
    }

    private boolean allFieldsShareSameTag(Map<String, String> snapshot) {
        return snapshot.values().stream().distinct().count() <= 1;
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private VideoControlRequest controlRequest(VideoActionType action, Double currentTime, Long clientActionTime) {
        return new VideoControlRequest(null, null, action, currentTime, null, clientActionTime);
    }
}
