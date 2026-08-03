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
import org.springframework.stereotype.Service;

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

    // 방 상태 적용
    public void applyControl(Long roomId, Long userId, VideoControlRequest req) {
        String key = VideoRedisKeys.videoKey(roomId);
        long nowMs = System.currentTimeMillis();

        VideoActionType action = req.getAction();
        if (action == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "action은 필수입니다.");
        }

        log.info("Applying control: roomId={}, userId={}, action={}, currentTime={}, videoId={}",
                roomId, userId, action, req.getCurrentTime(), req.getVideoId());

        switch (action) {
            case CHANGE_VIDEO -> {
                if (req.getVideoId() == null || req.getVideoId().isBlank()) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "CHANGE_VIDEO에는 videoId가 필요합니다.");
                }
                ops().put(key, VideoRedisKeys.F_VIDEO_ID, req.getVideoId());
                ops().put(key, VideoRedisKeys.F_STATUS, VideoRedisKeys.STATUS_PAUSED);
                ops().put(key, VideoRedisKeys.F_BASE_TIME, "0");
                ops().put(key, VideoRedisKeys.F_BASE_TS, String.valueOf(nowMs));
                ops().put(key, VideoRedisKeys.F_UPDATED_BY, String.valueOf(userId));
            }
            case PLAY -> {
                double t = requireTime(req);
                ops().put(key, VideoRedisKeys.F_STATUS, VideoRedisKeys.STATUS_PLAYING);
                ops().put(key, VideoRedisKeys.F_BASE_TIME, String.valueOf(t));
                ops().put(key, VideoRedisKeys.F_BASE_TS, String.valueOf(nowMs));
                ops().put(key, VideoRedisKeys.F_UPDATED_BY, String.valueOf(userId));
            }
            case PAUSE -> {
                double t = requireTime(req);
                ops().put(key, VideoRedisKeys.F_STATUS, VideoRedisKeys.STATUS_PAUSED);
                ops().put(key, VideoRedisKeys.F_BASE_TIME, String.valueOf(t));
                ops().put(key, VideoRedisKeys.F_BASE_TS, String.valueOf(nowMs));
                ops().put(key, VideoRedisKeys.F_UPDATED_BY, String.valueOf(userId));
            }
            case SEEK -> {
                double t = requireTime(req);
                ops().put(key, VideoRedisKeys.F_BASE_TIME, String.valueOf(t));
                ops().put(key, VideoRedisKeys.F_BASE_TS, String.valueOf(nowMs));
                ops().put(key, VideoRedisKeys.F_UPDATED_BY, String.valueOf(userId));
            }
        }
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
