package com.watchparty.watchparty.video.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VideoControlRequest {

    private Long roomId;
    private Long userId;

    private VideoActionType action;

    // SEEK/PLAY/PAUSE에서 현재 영상 시간
    private Double currentTime;

    // Change_VIDEO에서 사용
    private String videoId;
}
