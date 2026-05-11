package com.vocamaster.auth;

import com.vocamaster.auth.dto.LoginRequest;
import com.vocamaster.auth.dto.RegisterRequest;
import com.vocamaster.auth.dto.TokenPair;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.common.exception.UnauthorizedException;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final long REFRESH_EXPIRATION_DAYS = 14;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public TokenPair register(RegisterRequest req, String userAgent, String ip) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new BadRequestException("이미 사용 중인 이메일입니다");
        }
        User user = User.builder()
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .nickname(req.getNickname())
                .build();
        userRepository.save(user);
        return issueTokens(user, userAgent, ip);
    }

    @Transactional
    public TokenPair login(LoginRequest req, String userAgent, String ip) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다"));

        if (user.isDeleted()) {                 // ← 별도 if 블록 (비번 검증 전)
            throw new UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다");

        }
        return issueTokens(user, userAgent, ip);
    }

    /**
     * Refresh Token Rotation + Reuse Detection.
     *
     * 흐름:
     * 1) JWT 자체 검증 (서명 + 만료) + type=refresh 검증
     * 2) atomic UPDATE 시도 (CAS): WHERE token_hash=? AND revoked_at IS NULL
     * 3) affected=1 → 회전 성공, 새 access+refresh 발급
     * 4) affected=0 → 추가 SELECT
     *    - row 있고 revoked → REUSE → 그 사용자 모든 refresh 폐기 (mass logout) → 401
     *    - row 없음 / 만료 → 평범한 401
     */
    @Transactional
    public TokenPair refresh(String refreshToken, String userAgent, String ip) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException("유효하지 않은 토큰입니다");
        }
        if (!jwtProvider.validate(refreshToken)) {
            throw new UnauthorizedException("유효하지 않은 토큰입니다");
        }
        if (!"refresh".equals(jwtProvider.getType(refreshToken))) {
            throw new UnauthorizedException("유효하지 않은 토큰입니다");
        }

        String hash = sha256(refreshToken);
        LocalDateTime now = LocalDateTime.now();
        int affected = refreshTokenRepository.revokeIfActive(hash, now, ip);

        if (affected == 0) {
            // reuse detection 분기
            Optional<RefreshToken> row = refreshTokenRepository.findByTokenHash(hash);
            if (row.isPresent() && row.get().isRevoked()) {
                Long userId = jwtProvider.getUserId(refreshToken);
                refreshTokenRepository.revokeAllByUserId(userId, now);
                log.warn("Refresh token reuse detected — mass logout for userId={}", userId);
            }
            throw new UnauthorizedException("유효하지 않은 토큰입니다");
        }

        // rotation 성공 → 새 쌍 발급
        Long userId = jwtProvider.getUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("유효하지 않은 토큰입니다"));
        return issueTokens(user, userAgent, ip);
    }

    /**
     * Logout — 받은 refresh token만 revoke. 토큰 없거나 이미 폐기된 경우는 조용히 200 (idempotent).
     */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        String hash = sha256(refreshToken);
        refreshTokenRepository.revokeIfActive(hash, LocalDateTime.now(), null);
    }

    // === helpers ===

    private TokenPair issueTokens(User user, String userAgent, String ip) {
        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());

        RefreshToken row = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(refreshToken))
                .expiresAt(LocalDateTime.now().plusDays(REFRESH_EXPIRATION_DAYS))
                .userAgent(userAgent)
                .lastUsedIp(ip)
                .build();
        refreshTokenRepository.save(row);

        return new TokenPair(accessToken, refreshToken);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
