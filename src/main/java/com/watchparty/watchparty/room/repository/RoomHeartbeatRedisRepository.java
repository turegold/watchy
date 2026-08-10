package com.watchparty.watchparty.room.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/*
Redis 기반 "생존 신호(heartbeat)" 저장소

Key:
room:{roomId}:heartbeats (ZSET)

ZSET 구조:
- member: userId (String)
- score : 마지막 하트비트 시각(epoch millis)

참여자 목록(room:{roomId}:participants)의 score는 "입장 시각"(방장 위임 순서용)이라
생존 신호와는 목적이 달라서 별도 ZSET으로 관리한다.

용도:
- record: 클라이언트가 "저 살아있어요" 신호를 보낼 때마다 score 갱신
- findDeadUserIds: 마지막 신호가 cutoff 이전인(=응답 끊긴) userId 조회 → 스윕이 강제 퇴장
 */
@Repository
@RequiredArgsConstructor
public class RoomHeartbeatRedisRepository {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "room:";
    private static final String HEARTBEAT_SUFFIX = ":heartbeats";

    private static String heartbeatKey(Long roomId) {
        return KEY_PREFIX + roomId + HEARTBEAT_SUFFIX;
    }

    // 하트비트 기록(갱신)
    public void record(Long roomId, Long userId, long nowMillis) {
        Objects.requireNonNull(roomId, "roomId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        redisTemplate.opsForZSet().add(heartbeatKey(roomId), userId.toString(), nowMillis);
    }

    // 하트비트 제거(퇴장 시)
    public void remove(Long roomId, Long userId) {
        Objects.requireNonNull(roomId, "roomId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        redisTemplate.opsForZSet().remove(heartbeatKey(roomId), userId.toString());
    }

    // 방 하트비트 ZSET 전체 삭제(방 삭제 시)
    public void delete(Long roomId) {
        Objects.requireNonNull(roomId, "roomId must not be null");
        redisTemplate.delete(heartbeatKey(roomId));
    }

    // 마지막 하트비트 시각이 cutoffMillis 이하인(=오래 신호 없는) userId 목록
    public List<Long> findDeadUserIds(Long roomId, long cutoffMillis) {
        Objects.requireNonNull(roomId, "roomId must not be null");
        Set<String> dead = redisTemplate.opsForZSet()
                .rangeByScore(heartbeatKey(roomId), Double.NEGATIVE_INFINITY, cutoffMillis);
        if (dead == null || dead.isEmpty()) {
            return List.of();
        }
        return dead.stream().map(Long::valueOf).toList();
    }
}
