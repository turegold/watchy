package com.watchparty.watchparty.video.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VideoStateResponse {
    private String videoId;
    private String status; // PLAYING, PUASED 등
    private double currentTime; // 계산된 시간
    private long baseTimestamp; // 상태가 갱신된 시각
    private double baseTime; // 상태가 갱신된 시점의 영상 시간
    private Long updatedBy; // 마지막 조작자 userId
}
