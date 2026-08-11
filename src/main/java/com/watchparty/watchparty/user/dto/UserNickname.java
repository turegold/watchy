package com.watchparty.watchparty.user.dto;

// userId 여러 개의 닉네임을 한 쿼리로 조회할 때 쓰는 프로젝션 DTO
public record UserNickname(Long userId, String nickname) {
}
