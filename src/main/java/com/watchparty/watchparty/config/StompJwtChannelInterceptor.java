package com.watchparty.watchparty.config;

import com.watchparty.watchparty.auth.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class StompJwtChannelInterceptor implements ChannelInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        // CONNECT 프레임일 때만 인증 수행
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> authHeaders = accessor.getNativeHeader(AUTH_HEADER);

            if (authHeaders == null || authHeaders.isEmpty()) {
                return message;
            }

            String bearerToken = authHeaders.get(0);
            if (bearerToken == null || !bearerToken.startsWith(BEARER_PREFIX)) {
                return message;
            }

            String token = bearerToken.substring(BEARER_PREFIX.length());

            // AccessToken 유효성 검사
            JwtProvider.TokenValidationResult result = jwtProvider.validateAccessToken(token);
            if (result != JwtProvider.TokenValidationResult.VALID) {
                return message;
            }

            // userId 추출
            Optional<Long> userIdOpt = jwtProvider.getUserIdFromAccessToken(token);
            if (userIdOpt.isEmpty()) {
                return message;
            }

            Long userId = userIdOpt.get();

            // Principal로 User 심어두기
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            Collections.emptyList()
                    );

            accessor.setUser(authentication);
        }

        return message;
    }
}