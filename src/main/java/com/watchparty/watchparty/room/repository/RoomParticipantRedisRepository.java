package com.watchparty.watchparty.room.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/*
Redis 기반 "현재 참여자" 저장소

Key:
room:{roomId}:participants (ZSET)

ZSET 구조:
- member: userId (String)
- score: joinedAtMillis (임장 시각)

제공 기능:
- join: 참여자 추가 (입장시간 기록)
- leaveAndPickNextHost: 참여자 제거 + 남은 인원 수 + 다음 방장 후보(userId) 조회 (Lua로 원자 처리)
- count: 현재 참여자 수
 */

@Repository
@RequiredArgsConstructor
public class RoomParticipantRedisRepository {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "room:";
    private static final String PARTICIPANTS_SUFFIX = ":participants";

    private static  String participantsKey(Long roomId){
        return KEY_PREFIX + roomId + PARTICIPANTS_SUFFIX;
    }

    // 참여자 입장
    public void join(Long roomId, Long userId){
        Objects.requireNonNull(roomId, "roomId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");

        String key = participantsKey(roomId);
        long joinedAtMillis = Instant.now().toEpochMilli();

        Boolean ok = redisTemplate.opsForZSet()
                .add(key, userId.toString(), joinedAtMillis);

        if(ok == null){
            throw new IllegalStateException("Redis ZADD returned null");
        }
    }

    // 현재 참여자 수
    public long count(Long roomId){
        Objects.requireNonNull(roomId, "roomId must not be null");

        Long size = redisTemplate.opsForZSet().size(participantsKey(roomId));
        return size == null ? 0L : size;
    }

    // 퇴장 처리 + 다음 방장 후보 계산(Lua)
    // 반환:
    // - remainingCount: 퇴장 반영 후 남은 인원 수
    // - nextHostUserId: 남아있으면 입장순으로 가장 앞의 userId, 없으면 null

    public LeaveResult leaveAndPickNextHost(Long roomId, Long userId){
        Objects.requireNonNull(roomId, "roomId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");

        String key = participantsKey(roomId);

        // ARGV[1] = userId
        List raw = redisTemplate.execute(LEAVE_SCRIPT, Collections.singletonList(key), userId.toString());
        if(raw == null || raw.size() < 2){
            throw new IllegalStateException("Redis Lua script returned invalid result");
        }

        // LUA에서 number는 Long/Integer로 올 수 있음
        long remaining = toLong(raw.get(0));
        String nextHostStr = raw.get(1) == null ? null : raw.get(1).toString();
        Long nextHost = (nextHostStr == null || nextHostStr.isBlank()) ? null: Long.parseLong(nextHostStr);

        return new LeaveResult(remaining, nextHost);
    }

    // 방 참여ㅔ자 ZSET 자체를 삭제
    public void deleteRoomParticipants(Long roomId) {
        Objects.requireNonNull(roomId, "roomId must not be null");
        redisTemplate.delete(participantsKey(roomId));
    }

    private static long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Long l) return l;
        if (o instanceof Integer i) return i.longValue();
        if (o instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("Cannot convert to long: " + o);
    }

    public record LeaveResult(long remainingCount, Long nextHostUserId){}




    // Lua: userId 제거 -> 남은 인원 수 계산 -> 다음 방장 후보 조회
    private static final String LEAVE_AND_PICK_NEXT_HOST_LUA = """
            local key = KEYS[1]
            local userId = ARGV[1]

            -- 1) remove user
            redis.call('ZREM', key, userId)

            -- 2) count remaining
            local cnt = redis.call('ZCARD', key)

            -- 3) if empty, return {cnt, nil}
            if cnt == 0 then
              return {cnt, nil}
            end

            -- 4) pick earliest joined (lowest score)
            local nextHostArr = redis.call('ZRANGE', key, 0, 0)

            if nextHostArr == nil or #nextHostArr == 0 then
              return {cnt, nil}
            end

            return {cnt, nextHostArr[1]}
            """;

    private static final DefaultRedisScript<List> LEAVE_SCRIPT = new DefaultRedisScript<>();

    static{
        LEAVE_SCRIPT.setScriptText(LEAVE_AND_PICK_NEXT_HOST_LUA);
        LEAVE_SCRIPT.setResultType(List.class);
    }
}
