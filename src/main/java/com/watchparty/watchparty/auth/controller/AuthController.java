package com.watchparty.watchparty.auth.controller;

// Refresh Token으로 Access Token을 재발급하는 API 제공

import com.watchparty.watchparty.auth.dto.RefreshRequest;
import com.watchparty.watchparty.auth.service.AuthTokenService;
import com.watchparty.watchparty.common.exception.AppException;
import com.watchparty.watchparty.common.exception.ErrorCode;
import com.watchparty.watchparty.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthTokenService authTokenService;

    // Refresh Token으로 Access Token 재발급
    @PostMapping("/refresh")
    public ApiResponse<Map<String, String>> refresh(@Valid @RequestBody RefreshRequest request){
        String newAccessToken = authTokenService.reissueAccessToken(request.getRefreshToken());

        return ApiResponse.ok(
                "토큰 재발급 성공",
                Map.of("accessToken", newAccessToken)
        );
    }

    // 로그아웃
    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication authentication){
        Long userId = extractUserId(authentication);
        authTokenService.logout(userId);
        return ApiResponse.ok("로그아웃 성공", null);
    }

    // 인증 객체에서 userId 추출
    private Long extractUserId(Authentication authentication){
        if(authentication == null || authentication.getPrincipal() == null){
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Object principal = authentication.getPrincipal();

        if(principal instanceof Long){
            return (Long) principal;
        }

        if(principal instanceof  Integer){
            return ((Integer) principal).longValue();
        }

        if(principal instanceof String){
            try{
                return Long.parseLong((String) principal);
            }
            catch(NumberFormatException e){

            }
        }
        throw new AppException(ErrorCode.UNAUTHORIZED);
    }

}
