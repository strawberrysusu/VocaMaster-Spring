package com.vocamaster.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessExpiration;
    private final long refreshExpiration;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration}") long accessExpiration,
            @Value("${jwt.refresh-expiration}") long refreshExpiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
    }

    // Access 토큰: 1시간, type=access, email 포함
    public String createAccessToken(Long userId, String email) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())                  // jti
                .subject(userId.toString())
                .claim("email", email)
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpiration))
                .signWith(key)
                .compact();
    }

    // Refresh 토큰: 14일, type=refresh, 정보 최소화 (email 같은 거 X)
    public String createRefreshToken(Long userId) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())                  // jti
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(key)
                .compact();
    }

    // 토큰에서 userId 추출 (Subject)
    public Long getUserId(String token) {
        return Long.parseLong(parse(token).getSubject());
    }

    // 토큰의 type ("access" or "refresh") — /refresh 엔드포인트에서 검증용
    public String getType(String token) {
        return parse(token).get("type", String.class);
    }

    // 토큰의 email claim (access 토큰에만 들어 있음)
    public String getEmail(String token) {
        return parse(token).get("email", String.class);
    }

    // 토큰의 jti (고유 ID)
    public String getJti(String token) {
        return parse(token).getId();
    }

    // 토큰 유효성 검증 (서명 + 만료 통과해야 true)
    public boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
