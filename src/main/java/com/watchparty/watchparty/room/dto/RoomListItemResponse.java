package com.watchparty.watchparty.room.dto;

import lombok.Getter;

@Getter
public class RoomListItemResponse {

    private final Long roomId;
    private final String title;
    private final boolean isPrivate;

    private final Long hostUserId;
    private final String hostNickName;
    private final long participantCount;

    // 현재 재생 중인 영상 id (Redis). 방 목록 썸네일용. 없으면 null
    private final String videoId;

    // 방장 프로필 이미지 (presigned GET URL). 없으면 null → 클라에서 이니셜 아바타
    private final String hostProfileImageUrl;

    // JPQL projection용 생성자 (videoId/hostProfileImageUrl은 Redis·S3라 여기선 채우지 않음)
    public RoomListItemResponse(Long roomId,
                                String title,
                                boolean isPrivate,
                                Long hostUserId,
                                String hostNickName,
                                long participantCount) {
        this(roomId, title, isPrivate, hostUserId, hostNickName, participantCount, null, null);
    }

    public RoomListItemResponse(Long roomId,
                                String title,
                                boolean isPrivate,
                                Long hostUserId,
                                String hostNickName,
                                long participantCount,
                                String videoId,
                                String hostProfileImageUrl) {
        this.roomId = roomId;
        this.title = title;
        this.isPrivate = isPrivate;
        this.hostUserId = hostUserId;
        this.hostNickName = hostNickName;
        this.participantCount = participantCount;
        this.videoId = videoId;
        this.hostProfileImageUrl = hostProfileImageUrl;
    }

    // 참여자 수(Redis ZCARD)를 채운 복사본 (나머지 필드 보존)
    public RoomListItemResponse withParticipantCount(long participantCount) {
        return new RoomListItemResponse(roomId, title, isPrivate, hostUserId, hostNickName,
                participantCount, videoId, hostProfileImageUrl);
    }

    // videoId를 채운 복사본 (나머지 필드 보존)
    public RoomListItemResponse withVideoId(String videoId) {
        return new RoomListItemResponse(roomId, title, isPrivate, hostUserId, hostNickName,
                participantCount, videoId, hostProfileImageUrl);
    }

    // 방장 프로필 이미지 URL을 채운 복사본 (나머지 필드 보존)
    public RoomListItemResponse withHostProfileImageUrl(String hostProfileImageUrl) {
        return new RoomListItemResponse(roomId, title, isPrivate, hostUserId, hostNickName,
                participantCount, videoId, hostProfileImageUrl);
    }
}
