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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final PlatformTransactionManager txManager;

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
     * Refresh Token Rotation + Reuse Detection (P1-1 수리 반영).
     *
     * 구조 — "감지"와 "제재"의 시간 분리:
     * 1) JWT 자체 검증 (서명 + 만료 + type=refresh) — 트랜잭션 불필요
     * 2) [감지+회전 트랜잭션] atomic UPDATE(CAS) 시도
     *    - affected=1 → 회전 성공, 새 쌍 발급 (커밋)
     *    - affected=0 + row가 revoked → ReuseDetectedException으로 탈출 (롤백 = 락 해제)
     *    - 그 외 → 평범한 401
     * 3) [제재 — 트랜잭션 종료 후] 모든 refresh 폐기를 별도 커밋 → 401 유지
     *
     * 왜 이렇게: 제재를 감지 트랜잭션 안에서 하면 401 롤백에 제재까지 증발하고(버그 원형),
     * REQUIRES_NEW 옆방으로 빼면 감지 트랜잭션이 잡은 행 락과 충돌해 부모-자식 데드락.
     * → 감지 트랜잭션을 먼저 끝내(락 해제) 놓고 제재하는 게 유일하게 안전한 순서.
     *
     * ⚠️ 계약: 이 메서드는 활성 트랜잭션 "없이" 호출되어야 한다 (컨트롤러 직행 전용).
     * 두 방은 전파 기본값(REQUIRED)이라, 바깥 트랜잭션 안에서 부르면 거기에 합류해
     * 감지/제재 경계가 사라지고 원버그가 부활한다. (전파를 REQUIRES_NEW로 강제하지 않는
     * 이유: 바깥이 락을 쥔 채 부르는 순간 위의 데드락이 재발 — 계약+커밋 경계 테스트로 고정)
     */
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

        try {
            // 감지+회전 트랜잭션 — 프로그래매틱 경계(TransactionTemplate)라 self-invocation 함정 없음
            return new TransactionTemplate(txManager).execute(status -> {
                int affected = refreshTokenRepository.revokeIfActive(hash, now, ip);

                if (affected == 0) {
                    Optional<RefreshToken> row = refreshTokenRepository.findByTokenHash(hash);
                    if (row.isPresent() && row.get().isRevoked()) {
                        // 제재는 여기서 하지 않는다 — 사실만 알리고 탈출 (이 트랜잭션의 락과 충돌하므로)
                        throw new ReuseDetectedException(jwtProvider.getUserId(refreshToken));
                    }
                    throw new UnauthorizedException("유효하지 않은 토큰입니다");
                }

                Long userId = jwtProvider.getUserId(refreshToken);
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new UnauthorizedException("유효하지 않은 토큰입니다"));
                return issueTokens(user, userAgent, ip);
            });
        } catch (ReuseDetectedException e) {
            // 제재 트랜잭션 — 감지 방은 이미 롤백·락 해제된 뒤라 충돌 없음.
            // (@Modifying 쿼리는 스스로 트랜잭션을 열지 않으므로 명시적으로 방을 만들어야 함)
            // 여기서 커밋되므로 아래 401 rethrow와 무관하게 제재가 생존 (P1-1)
            new TransactionTemplate(txManager).execute(status -> {
                refreshTokenRepository.revokeAllByUserId(e.getUserId(), now);
                return null;
            });
            log.warn("Refresh token reuse detected — mass logout for userId={}", e.getUserId());
            throw e;
        }
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
