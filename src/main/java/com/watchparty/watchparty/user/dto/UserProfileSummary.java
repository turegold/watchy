package com.watchparty.watchparty.user.dto;

// 닉네임 + 프로필 이미지 key를 한 번에 담는 프로젝션 DTO (채팅 메시지 조립 시 쿼리 2번 → 1번으로 줄이려고 씀)
public record UserProfileSummary(String nickname, String profileImageKey) {
}
