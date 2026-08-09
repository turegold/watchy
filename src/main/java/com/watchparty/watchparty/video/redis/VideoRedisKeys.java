package com.watchparty.watchparty.video.redis;

public class VideoRedisKeys {
    public static String videoKey(Long roomId){
        return "room:" + roomId + ":video";
    }

    public static final String F_VIDEO_ID = "videoId";
    public static final String F_STATUS = "status";
    public static final String F_BASE_TIME = "baseTime";
    public static final String F_BASE_TS = "baseTimestamp";
    public static final String F_UPDATED_BY = "updatedBy";
    // 클라이언트가 액션을 발생시킨 시각(ms). baseTimestamp(서버 기준 경과시간 계산용)와는 별개로,
    // "이 액션이 더 최신인가"만 판단하는 버전 값으로 사용 (CAS 비교 기준)
    public static final String F_ACTION_TS = "actionTs";

    public static final String STATUS_PLAYING = "PLAYING";
    public static final String STATUS_PAUSED = "PAUSED";
}
