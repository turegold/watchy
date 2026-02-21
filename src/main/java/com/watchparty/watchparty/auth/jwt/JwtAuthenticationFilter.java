package com.watchparty.watchparty.auth.jwt;

import com.watchparty.watchparty.common.exception.AppException;
import com.watchparty.watchparty.common.exception.ErrorCode;
import com.watchparty.watchparty.common.security.JwtAuthenticationEntryPoint;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);

        if (token != null) {
            try {
                // Access Token 검증
                JwtProvider.TokenValidationResult result = jwtProvider.validateAccessToken(token);

                if (result == JwtProvider.TokenValidationResult.EXPIRED) {
                    throw new AppException(ErrorCode.EXPIRED_TOKEN);
                }
                if (result == JwtProvider.TokenValidationResult.INVALID) {
                    throw new AppException(ErrorCode.INVALID_TOKEN);
                }

                // userId 추출
                Long userId = jwtProvider.getUserIdFromAccessToken(token)
                        .orElseThrow(()-> new AppException(ErrorCode.INVALID_TOKEN));

                // 인증 객체 세팅
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER"))
                        );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (AppException e) {
                SecurityContextHolder.clearContext();
                request.setAttribute("appException", e);
                jwtAuthenticationEntryPoint.commence(
                        request,
                        response,
                        new BadCredentialsException(e.getMessage(), e)
                );
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");

        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }

        return null;
    }
}
