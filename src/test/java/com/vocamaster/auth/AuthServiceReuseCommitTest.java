package com.vocamaster.auth;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.auth.dto.RegisterRequest;
import com.vocamaster.auth.dto.TokenPair;
import com.vocamaster.common.exception.UnauthorizedException;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-1 검증 — reuse detection의 mass logout이 "실제로 커밋"되는지.
 *
 * 기존 AuthServiceTest의 재사용 테스트는 클래스 @Transactional 안에서 돌아서
 * 운영의 커밋/롤백 경계를 재현하지 못함 (서비스 트랜잭션이 테스트 트랜잭션에 합류).
 * 여기서는 자동 롤백을 꺼서(NOT_SUPPORTED) 서비스 호출마다 운영과 동일하게
 * 트랜잭션이 열리고 닫히게 한다. 데이터 청소는 @AfterEach에서 수동 (FK 역순).
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthServiceReuseCommitTest extends AbstractIntegrationTest {

    private static final String UA = "test-agent";
    private static final String IP = "127.0.0.1";

    @Autowired private AuthService authService;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UserRepository userRepository;

    private final String email = "reuse_commit_" + System.nanoTime() + "@test.com";

    @AfterEach
    void cleanUp() {
        userRepository.findByEmail(email).ifPresent(user -> {
            refreshTokenRepository.deleteAll(refreshTokenRepository.findAllByUserId(user.getId()));
            userRepository.delete(user);
        });
    }

    @Test
    @DisplayName("재사용 감지의 mass logout은 401 롤백에도 살아남아야 한다 — R1까지 죽어야 통과")
    void reuseDetection_massLogout_mustSurviveRollback() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail(email);
        req.setPassword("password123");
        req.setNickname("커밋검증");

        TokenPair initial = authService.register(req, UA, IP);                      // R0 발급 (커밋)
        TokenPair rotated = authService.refresh(initial.refreshToken(), UA, IP);    // R0 → R1 회전 (커밋)

        // 폐기된 R0 재사용 → 401. 이 요청의 트랜잭션은 예외로 롤백됨
        assertThrows(UnauthorizedException.class, () ->
                authService.refresh(initial.refreshToken(), UA, IP));

        // 진짜 시험: mass logout이 커밋됐다면 R1도 이미 폐기 → 거부돼야 한다.
        // 버그 상태에서는 mass logout이 롤백에 증발해 R1 회전이 '성공' → 이 단언이 깨짐 (빨간불)
        assertThrows(UnauthorizedException.class, () ->
                        authService.refresh(rotated.refreshToken(), UA, IP),
                "mass logout이 롤백으로 증발하면 R1이 살아있어 여기서 실패한다 (P1-1)");
    }
}
