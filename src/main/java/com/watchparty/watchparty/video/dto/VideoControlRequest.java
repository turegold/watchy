package com.watchparty.watchparty.video.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor // 테스트에서 픽스처 생성용. JSON 역직렬화는 기존과 동일하게 무인자 생성자 사용.
public class VideoControlRequest {

    private Long roomId;
    private Long userId;

    private VideoActionType action;

    // SEEK/PLAY/PAUSE에서 현재 영상 시간
    private Double currentTime;

    // Change_VIDEO에서 사용
    private String videoId;

    // 클라이언트가 이 액션을 발생시킨(버튼 누른) 시각(ms, Date.now()).
    // 서버 도착 순서가 아니라 이 값으로 최신 여부를 판단해 네트워크 지연으로 인한 순서 역전을 방지.
    private Long clientActionTime;
}
