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

    public static final String STATUS_PLAYING = "PLAYING";
    public static final String STATUS_PAUSED = "PAUSED";
}
