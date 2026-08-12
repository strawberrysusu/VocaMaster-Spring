package com.vocamaster.auth;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.auth.dto.LoginRequest;
import com.vocamaster.common.exception.TooManyRequestsException;
import com.vocamaster.common.exception.UnauthorizedException;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 로그인 rate limit 검증 (ADR-034).
 *
 * 테스트 기본은 ratelimit off(공용 Redis 오염 방지) — 이 클래스만 켠다.
 * Redis는 전용 컨테이너로 격리해서 dev Redis의 상태에 영향받지 않게 한다.
 */
@TestPropertySource(properties = "ratelimit.login.enabled=true")
class LoginAttemptServiceTest extends AbstractIntegrationTest {

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379)
            .withReuse(true);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired private LoginAttemptService loginAttemptService;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private StringRedisTemplate redis;

    private String email;       // 테스트마다 유니크 — 컨테이너 재사용 시 이전 실행 잔재 차단

    @BeforeEach
    void setUp() {
        email = "ratelimit_" + System.nanoTime() + "@test.com";
    }

    @Test
    @DisplayName("4회까지는 통과, 5회째에 잠금 → 429")
    void locksAfterFiveFailures() {
        for (int i = 0; i < 4; i++) {
            loginAttemptService.recordFailure(email);
            assertDoesNotThrow(() -> loginAttemptService.assertNotLocked(email),
                    "4회까지는 아직 잠기면 안 됨");
        }

        loginAttemptService.recordFailure(email);   // 5회째

        TooManyRequestsException ex = assertThrows(TooManyRequestsException.class,
                () -> loginAttemptService.assertNotLocked(email));
        assertTrue(ex.getRetryAfterSeconds() > 0 && ex.getRetryAfterSeconds() <= 1800,
                "Retry-After는 30분 잠금 잔여 시간");
    }

    @Test
    @DisplayName("존재하지 않는 이메일도 동일하게 잠긴다 — 401/429 차이로 계정 존재가 새면 안 됨")
    void unknownEmailIsCountedToo() {
        String ghost = "ghost_" + System.nanoTime() + "@nowhere.com";
        assertFalse(userRepository.findByEmail(ghost).isPresent(), "DB에 없는 계정임을 확인");

        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure(ghost);
        }

        assertThrows(TooManyRequestsException.class,
                () -> loginAttemptService.assertNotLocked(ghost),
                "없는 계정만 429가 안 뜨면 그 차이가 곧 회원 명단 누설");
    }

    @Test
    @DisplayName("대소문자만 바꿔도 같은 카운터 — 정규화로 우회 차단")
    void emailIsCaseInsensitive() {
        String lower = "case_" + System.nanoTime() + "@test.com";
        String upper = lower.toUpperCase();

        for (int i = 0; i < 3; i++) loginAttemptService.recordFailure(lower);
        for (int i = 0; i < 2; i++) loginAttemptService.recordFailure(upper);   // 합계 5회

        assertThrows(TooManyRequestsException.class,
                () -> loginAttemptService.assertNotLocked(lower));
    }

    @Test
    @DisplayName("첫 실패에만 TTL — 창이 계속 밀리지 않는지")
    void windowTtlSetOnFirstFailureOnly() throws InterruptedException {
        loginAttemptService.recordFailure(email);
        Long firstTtl = redis.getExpire("login:fail:" + email, TimeUnit.SECONDS);

        Thread.sleep(1100);                          // 1초 이상 경과
        loginAttemptService.recordFailure(email);
        Long secondTtl = redis.getExpire("login:fail:" + email, TimeUnit.SECONDS);

        assertNotNull(firstTtl);
        assertNotNull(secondTtl);
        assertTrue(secondTtl < firstTtl,
                "두 번째 실패로 TTL이 갱신되면 창이 밀려 영원히 안 풀린다");
    }

    @Test
    @DisplayName("로그인 성공 시 카운터 리셋 — 오타 흔적이 누적되지 않음")
    void resetOnSuccess() {
        userRepository.save(User.builder()
                .email(email).password(passwordEncoder.encode("correct-pw")).nickname("tester").build());

        for (int i = 0; i < 4; i++) {
            assertThrows(UnauthorizedException.class, () -> authService.login(loginReq("wrong-pw"), "ua", "1.1.1.1"));
        }
        assertNotNull(redis.opsForValue().get("login:fail:" + email), "4회 실패가 쌓여 있어야");

        authService.login(loginReq("correct-pw"), "ua", "1.1.1.1");

        assertNull(redis.opsForValue().get("login:fail:" + email), "성공하면 카운터가 사라져야");
    }

    @Test
    @DisplayName("실제 로그인 5회 실패 → 6회째는 401이 아니라 429 (전 구간 연결)")
    void loginThroughServiceGetsLocked() {
        userRepository.save(User.builder()
                .email(email).password(passwordEncoder.encode("correct-pw")).nickname("tester").build());

        for (int i = 0; i < 5; i++) {
            assertThrows(UnauthorizedException.class, () -> authService.login(loginReq("wrong-pw"), "ua", "1.1.1.1"));
        }

        assertThrows(TooManyRequestsException.class,
                () -> authService.login(loginReq("wrong-pw"), "ua", "1.1.1.1"));
        // 비번이 맞아도 잠금 중에는 막힌다 (잠금은 계정 단위 방어)
        assertThrows(TooManyRequestsException.class,
                () -> authService.login(loginReq("correct-pw"), "ua", "1.1.1.1"));
    }

    private LoginRequest loginReq(String password) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }
}
