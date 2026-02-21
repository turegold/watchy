package com.watchparty.watchparty.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtProvider {

    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String TOKEN_TYPE_ACCESS = "ACCESS";
    private static final String TOKEN_TYPE_REFRESH = "REFRESH";

    public enum TokenValidationResult{
        VALID, EXPIRED, INVALID
    }

    // JWT 서명에 사용할 비밀키
    @Value("${jwt.secret}")
    private String secretKey;

    // Access Token 만료 시간
    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    // Refresh Token 만료 시간
    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    // Key
    private Key signingKey;

    // Access Token 생성
    public String createAccessToken(Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(now)
                .setExpiration(expiry)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // Refresh Token 생성
    public String createRefreshToken(Long userId){
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenExpiration);

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(now)
                .setExpiration(expiry)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // Access Token 검증
    public TokenValidationResult validateAccessToken(String token){
        return validate(token, TOKEN_TYPE_ACCESS);
    }

    // Refresh Token 검증
    public TokenValidationResult validateRefreshToken(String token){
        return validate(token, TOKEN_TYPE_REFRESH);
    }

    // Access Token에서 userId 추출
    public Optional<Long> getUserIdFromAccessToken(String token){
        return parseSubjectIfValidType(token, TOKEN_TYPE_ACCESS).flatMap(this::toLong);
    }

    // Refresh Token에서 userId 추출
    public Optional<Long> getUserIdFromRefreshToken(String token){
        return parseSubjectIfValidType(token, TOKEN_TYPE_REFRESH).flatMap(this::toLong);
    }

    // Refresh Token의 만료일시를 LocalDateTime으로 변환
    public Optional<LocalDateTime> getRefreshTokenExpiryDateTime(String refreshToken){
        try{
            Claims claims = parseClaims(refreshToken);
            Date exp = claims.getExpiration();
            return Optional.of(LocalDateTime.ofInstant(exp.toInstant(), ZoneId.systemDefault()));
        }
        catch(JwtException | IllegalArgumentException e){
            return Optional.empty();
        }
    }

    // 서명 검증
    private TokenValidationResult validate(String token, String expectedType){
        try{
            Claims claims = parseClaims(token);
            String type = claims.get(CLAIM_TOKEN_TYPE, String.class);

            if(!expectedType.equals(type)){
                return TokenValidationResult.INVALID;
            }
            return TokenValidationResult.VALID;
        }
        catch(ExpiredJwtException e){
            return TokenValidationResult.EXPIRED;
        }
        catch(JwtException | IllegalArgumentException e){
            return TokenValidationResult.INVALID;
        }
    }

    // tokenType이 기대값일 때만 subject를 반환
    private Optional<String> parseSubjectIfValidType(String token, String expectedType){
        try{
            Claims claims = parseClaims(token);
            String type = claims.get(CLAIM_TOKEN_TYPE, String.class);

            if(!expectedType.equals(type)){
                return Optional.empty();
            }
            return Optional.ofNullable(claims.getSubject());
        }
        catch(JwtException | IllegalArgumentException e){
            return Optional.empty();
        }
    }

    // Claims 파싱
    // Claims: JWT에서 payload를 의미하는 객체
    private Claims parseClaims(String token){
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // JWT 서명용 Key 생성
    private Key getSigningKey() {
        if(signingKey == null) {
            signingKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        }
        return signingKey;
    }

    // String을 Long으로 변환
    private Optional<Long> toLong(String value){
        try{
            return Optional.of(Long.parseLong(value));
        }
        catch (NumberFormatException e){
            return Optional.empty();
        }
    }
}
