package com.watchparty.watchparty.auth.service;

import com.watchparty.watchparty.auth.entity.RefreshToken;
import com.watchparty.watchparty.auth.jwt.JwtProvider;
import com.watchparty.watchparty.auth.repository.RefreshTokenRepository;
import com.watchparty.watchparty.common.exception.AppException;
import com.watchparty.watchparty.common.exception.ErrorCode;
import com.watchparty.watchparty.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Service
public class AuthTokenService {

    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    // Refresh Token DB 저장/갱신
    // 로그인 성공 직후, 새 refreshToken을  발급해서 DB에 저장
    @Transactional
    public void upsertRefreshToken(Long userId, String refreshToken){
        // refresh token 문자열에서 exp를 꺼내서 DB expiryDate로 저장
        LocalDateTime expiryDate = jwtProvider.getRefreshTokenExpiryDateTime(refreshToken)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_REFRESH_TOKEN));

        RefreshToken entity = refreshTokenRepository.findByUserId(userId)
                .map(rt -> {
                    // 이미 있으면 갱신
                    rt.update(refreshToken, expiryDate);
                    return rt;
                })
                .orElseGet(() -> {
                    // 없으면 새로 생성
                    return RefreshToken.builder()
                            .user(User.referenceById(userId))
                            .token(refreshToken)
                            .expiryDate(expiryDate)
                            .build();
                });
        refreshTokenRepository.save(entity);
    }

    // Refresh Token으로 Access Token 재발급
    @Transactional(readOnly = true)
    public String reissueAccessToken(String refreshToken){

        // Refresh Token JWT 검증
        JwtProvider.TokenValidationResult result = jwtProvider.validateRefreshToken(refreshToken);

        if(result == JwtProvider.TokenValidationResult.INVALID){
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if(result == JwtProvider.TokenValidationResult.EXPIRED){
            throw new AppException(ErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        // DB에 존재하는지 확인
        RefreshToken saved = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new AppException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        // DB 만료 체크
        if(saved.isExpired(LocalDateTime.now())){
            throw new AppException(ErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        // userId 추출
        Long userId = jwtProvider.getUserIdFromRefreshToken(refreshToken)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_REFRESH_TOKEN));

        // 새 Access Token 발급
        return jwtProvider.createAccessToken(userId);
    }

    // 로그아웃
    @Transactional
    public void logout(Long userId){
        refreshTokenRepository.deleteByUser_Id(userId);
    }
}
