package com.watchparty.watchparty.video.service;

import com.watchparty.watchparty.common.exception.AppException;
import com.watchparty.watchparty.common.exception.ErrorCode;
import com.watchparty.watchparty.video.dto.VideoActionType;
import com.watchparty.watchparty.video.dto.VideoControlRequest;
import com.watchparty.watchparty.video.dto.VideoStateResponse;
import com.watchparty.watchparty.video.redis.VideoRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoStateService {

    private final StringRedisTemplate redisTemplate;

    private HashOperations<String, String, String> ops() {
        return redisTemplate.opsForHash();
    }

    // 방 상태 조회
    public VideoStateResponse getState(Long roomId) {
        String key = VideoRedisKeys.videoKey(roomId);
        Map<String, String> data = ops().entries(key);

        if (data == null || data.isEmpty()) {
            log.debug("Video state empty: roomId={}", roomId);
            return new VideoStateResponse(
                    null,
                    VideoRedisKeys.STATUS_PAUSED,
                    0.0,
                    0L,
                    0.0,
                    null
            );
        }

        String videoId = data.get(VideoRedisKeys.F_VIDEO_ID);
        String status = data.getOrDefault(VideoRedisKeys.F_STATUS, VideoRedisKeys.STATUS_PAUSED);

        double baseTime = parseDouble(data.get(VideoRedisKeys.F_BASE_TIME), 0.0);
        long baseTs = parseLong(data.get(VideoRedisKeys.F_BASE_TS), 0L);
        Long updatedBy = parseLongObj(data.get(VideoRedisKeys.F_UPDATED_BY));

        double currentTime = computeCurrentTime(status, baseTime, baseTs);

        log.debug("Video state loaded: roomId={}, status={}, videoId={}, currentTime={}",
                roomId, status, videoId, currentTime);
        return new VideoStateResponse(
                videoId,
                status,
                currentTime,
                baseTs,
                baseTime,
                updatedBy
        );
    }

    // 방 상태 적용 (Lua 스크립트로 원자적 CAS: torn read 방지 + 순서 역전 방지)
    // 반환값: 이 액션이 실제로 반영됐으면 true, 더 최신 액션이 이미 반영돼 있어 무시됐으면 false
    public boolean applyControl(Long roomId, Long userId, VideoControlRequest req) {
        String key = VideoRedisKeys.videoKey(roomId);
        long nowMs = System.currentTimeMillis();

        VideoActionType action = req.getAction();
        if (action == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "action은 필수입니다.");
        }

        log.info("Applying control: roomId={}, userId={}, action={}, currentTime={}, videoId={}",
                roomId, userId, action, req.getCurrentTime(), req.getVideoId());

        // 이번 액션으로 반영할 필드들 (field, value, field, value ...)
        List<String> fields = new ArrayList<>();
        switch (action) {
            case CHANGE_VIDEO -> {
                if (req.getVideoId() == null || req.getVideoId().isBlank()) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "CHANGE_VIDEO에는 videoId가 필요합니다.");
                }
                addField(fields, VideoRedisKeys.F_VIDEO_ID, req.getVideoId());
                addField(fields, VideoRedisKeys.F_STATUS, VideoRedisKeys.STATUS_PAUSED);
                addField(fields, VideoRedisKeys.F_BASE_TIME, "0");
                addField(fields, VideoRedisKeys.F_BASE_TS, String.valueOf(nowMs));
                addField(fields, VideoRedisKeys.F_UPDATED_BY, String.valueOf(userId));
            }
            case PLAY -> {
                double t = requireTime(req);
                addField(fields, VideoRedisKeys.F_STATUS, VideoRedisKeys.STATUS_PLAYING);
                addField(fields, VideoRedisKeys.F_BASE_TIME, String.valueOf(t));
                addField(fields, VideoRedisKeys.F_BASE_TS, String.valueOf(nowMs));
                addField(fields, VideoRedisKeys.F_UPDATED_BY, String.valueOf(userId));
            }
            case PAUSE -> {
                double t = requireTime(req);
                addField(fields, VideoRedisKeys.F_STATUS, VideoRedisKeys.STATUS_PAUSED);
                addField(fields, VideoRedisKeys.F_BASE_TIME, String.valueOf(t));
                addField(fields, VideoRedisKeys.F_BASE_TS, String.valueOf(nowMs));
                addField(fields, VideoRedisKeys.F_UPDATED_BY, String.valueOf(userId));
            }
            case SEEK -> {
                double t = requireTime(req);
                addField(fields, VideoRedisKeys.F_BASE_TIME, String.valueOf(t));
                addField(fields, VideoRedisKeys.F_BASE_TS, String.valueOf(nowMs));
                addField(fields, VideoRedisKeys.F_UPDATED_BY, String.valueOf(userId));
            }
        }

        long clientActionTime = resolveClientActionTime(req, nowMs);

        List<Object> luaArgs = new ArrayList<>();
        luaArgs.add(String.valueOf(clientActionTime));
        luaArgs.addAll(fields);

        Long applied = redisTemplate.execute(COMPARE_AND_SET_SCRIPT, List.of(key), luaArgs.toArray());
        boolean accepted = applied != null && applied == 1L;

        if (!accepted) {
            log.warn("Stale video control ignored (더 최신 액션이 이미 반영됨): roomId={}, userId={}, action={}, clientActionTime={}",
                    roomId, userId, action, clientActionTime);
        }
        return accepted;
    }

    private void addField(List<String> fields, String name, String value) {
        fields.add(name);
        fields.add(value);
    }

    // 클라이언트가 안 보냈으면(구버전 클라이언트 등) 서버 도착 시각으로 degrade — 순서 보장은 못 하지만 기존과 동일하게 동작
    private long resolveClientActionTime(VideoControlRequest req, long fallbackNowMs) {
        Long clientTime = req.getClientActionTime();
        return clientTime != null ? clientTime : fallbackNowMs;
    }

    // KEYS[1] = video 상태 해시 키
    // ARGV[1] = 이번 액션의 clientActionTime (버전 비교용)
    // ARGV[2..] = 반영할 field/value 쌍
    // 저장된 actionTs보다 크지 않으면(옛날/중복 액션) 아무것도 안 쓰고 0 반환 → torn read도, 순서 역전도 불가능
    private static final String COMPARE_AND_SET_LUA = """
            local key = KEYS[1]
            local incomingTs = tonumber(ARGV[1])

            local storedTsStr = redis.call('HGET', key, 'actionTs')
            local storedTs = storedTsStr and tonumber(storedTsStr) or nil

            if storedTs ~= nil and incomingTs <= storedTs then
                return 0
            end

            local args = {key, 'actionTs', ARGV[1]}
            for i = 2, #ARGV do
                args[#args + 1] = ARGV[i]
            end

            redis.call('HSET', unpack(args))
            return 1
            """;

    private static final DefaultRedisScript<Long> COMPARE_AND_SET_SCRIPT = new DefaultRedisScript<>();

    static {
        COMPARE_AND_SET_SCRIPT.setScriptText(COMPARE_AND_SET_LUA);
        COMPARE_AND_SET_SCRIPT.setResultType(Long.class);
    }

    // videoId만 가볍게 조회 (방 목록 썸네일용). 없으면 null
    public String getVideoId(Long roomId) {
        return ops().get(VideoRedisKeys.videoKey(roomId), VideoRedisKeys.F_VIDEO_ID);
    }

    // 영상 삭제
    public void deleteState(Long roomId){
        String key = VideoRedisKeys.videoKey(roomId);
        Boolean deleted = redisTemplate.delete(key);
        log.info("Video state redis key deleted: roomId={}, key={}, deleted={}", roomId, key, deleted);
    }

    // 시간 계산
    private double computeCurrentTime(String status, double baseTime, long baseTs) {
        if (!VideoRedisKeys.STATUS_PLAYING.equals(status)) return baseTime;
        if (baseTs <= 0) return baseTime;

        long now = System.currentTimeMillis();
        double elapsedSec = (now - baseTs) / 1000.0;
        double t = baseTime + elapsedSec;

        return Math.max(0.0, t);
    }

    private double requireTime(VideoControlRequest req) {
        if (req.getCurrentTime() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, req.getAction() + "에는 currentTime이 필요합니다.");
        }
        return req.getCurrentTime();
    }

    private double parseDouble(String v, double def) {
        try {
            return v == null ? def : Double.parseDouble(v);
        } catch (Exception e) {
            return def;
        }
    }

    private long parseLong(String v, long def) {
        try {
            return v == null ? def : Long.parseLong(v);
        } catch (Exception e) {
            return def;
        }
    }

    private Long parseLongObj(String v) {
        try {
            return v == null ? null : Long.parseLong(v);
        } catch (Exception e) {
            return null;
        }
    }
}
