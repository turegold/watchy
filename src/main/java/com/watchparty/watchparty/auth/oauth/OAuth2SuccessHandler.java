package com.watchparty.watchparty.auth.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.watchparty.watchparty.auth.jwt.JwtProvider;
import com.watchparty.watchparty.auth.service.AuthTokenService;
import com.watchparty.watchparty.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * 로그인 성공 시 해야 하는 일:
 * 1) Access Token 발급
 * 2) Refresh Token 발급
 * 3) Refresh Token DB 저장/갱신
 * 4) 응답으로 accessToken + refreshToken 반환
 *
 * 여기서 refresh로 access 재발급을 하는게 아니라,
 * 재발급은 /api/auth/refresh API에서 함
 */
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final AuthTokenService authTokenService;
    private final ObjectMapper objectMapper;

    @Value("${FRONTEND_BASE_URL}")
    private String frontendBaseUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        Long userId = ((Number) oAuth2User.getAttribute("userId")).longValue();

        // Access Token 발급
        String accessToken = jwtProvider.createAccessToken(userId);

        // Refresh Token 발급
        String refreshToken = jwtProvider.createRefreshToken(userId);

        // Refresh Token DB 저장/갱신
        authTokenService.upsertRefreshToken(userId, refreshToken);

        // SecurityContext에 인증 주입
        UsernamePasswordAuthenticationToken newAuth =
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        Collections.singleton(new SimpleGrantedAuthority("ROLE_USER"))
                );
        SecurityContextHolder.getContext().setAuthentication(newAuth);

        // 공통 응답 포맷으로 반환
        ApiResponse<Map<String, String>> body = ApiResponse.ok(
                "로그인에 성공했습니다.",
                Map.of("accessToken", accessToken,
                        "refreshToken", refreshToken)
        );

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String redirectUrl = frontendBaseUrl + "/auth/callback"
                + "?accessToken=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8)
                + "&refreshToken=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8);

        response.sendRedirect(redirectUrl);
    }
}
