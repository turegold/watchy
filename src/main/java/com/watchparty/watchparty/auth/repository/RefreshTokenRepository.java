package com.watchparty.watchparty.auth.repository;

import com.watchparty.watchparty.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // 유저 ID로 refreshToken 조회
    Optional<RefreshToken> findByUserId(Long userId);

    // refreshToken 문자열로 refreshToken 조회
    Optional<RefreshToken> findByToken(String token);

    // 유저 ID로 refreshToken 삭제
    void deleteByUser_Id(Long userId);
}
