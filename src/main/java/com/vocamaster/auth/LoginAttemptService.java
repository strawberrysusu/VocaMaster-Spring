package com.vocamaster.auth;

import com.vocamaster.common.exception.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 로그인 무차별 대입 방어 — 고정 창 카운터 (ADR-034).
 *
 * 정책: 5분 안에 5회 실패 → 30분 잠금 → 429.
 *
 * 설계 3원칙:
 * 1) **이메일 존재 여부와 무관하게 카운트** — 있는 계정만 세면 401/429 차이로 회원 명단이 샌다
 * 2) **fail-open** — Redis 장애 시 제한을 포기하고 통과시킨다. 인증 자체가 막히는 게 더 큰 사고
 * 3) **TTL은 첫 증가에서만** — 매번 갱신하면 창이 계속 밀려 영원히 안 풀린다
 *
 * Redis 쓰기는 JPA 트랜잭션 밖이라 401 롤백에도 실패 기록이 남는다 (P1-1에서 제재가 롤백에
 * 증발했던 것과 반대 방향의 같은 원리 — 여기서는 그 성질이 우리 편).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final Duration LOCK = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;

    @Value("${ratelimit.login.enabled:true}")
    private boolean enabled;

    /** 잠금 상태면 429. 로그인 처리 맨 앞에서 호출 — 잠긴 계정은 DB 조회조차 하지 않는다. */
    public void assertNotLocked(String email) {
        if (!enabled) return;

        Long remaining = lockRemainingSeconds(normalize(email));
        if (remaining != null && remaining > 0) {           // throw는 try 밖에서 — fail-open catch에 먹히면 안 됨
            throw new TooManyRequestsException(
                    "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해주세요", remaining);
        }
    }

    /** 실패 1건 기록. 존재하지 않는 이메일도 동일하게 센다 (누설 방지). */
    public void recordFailure(String email) {
        if (!enabled) return;

        String key = normalize(email);
        try {
            Long count = redis.opsForValue().increment(failKey(key));   // 원자적 — 동시 시도도 안 샘
            if (count == null) return;

            if (count == 1L) {
                redis.expire(failKey(key), WINDOW);                     // 창은 첫 실패 시각부터 고정
            }
            if (count >= MAX_ATTEMPTS) {
                redis.opsForValue().set(lockKey(key), "1", LOCK);
                redis.delete(failKey(key));                             // 잠갔으니 카운터 정리 — 해제 후 새 창
                log.warn("로그인 잠금 발동 — {}회 실패, {}분간 차단", count, LOCK.toMinutes());
            }
        } catch (DataAccessException e) {
            log.warn("rate limit 기록 실패 — Redis 연결 문제로 이번 실패는 집계되지 않음", e);
        }
    }

    /** 로그인 성공 시 호출 — 정상 사용자가 오타 몇 번 낸 흔적을 지운다. */
    public void reset(String email) {
        if (!enabled) return;

        String key = normalize(email);
        try {
            redis.delete(failKey(key));
            redis.delete(lockKey(key));
        } catch (DataAccessException e) {
            log.warn("rate limit 초기화 실패 — Redis 연결 문제", e);
        }
    }

    /** 잠금 잔여 초. 잠기지 않았거나 Redis를 못 읽으면 null (= 통과). */
    private Long lockRemainingSeconds(String normalizedEmail) {
        try {
            return redis.getExpire(lockKey(normalizedEmail), TimeUnit.SECONDS);
        } catch (DataAccessException e) {
            log.warn("rate limit 비활성 — Redis 연결 실패로 이번 요청은 제한 없이 통과 (fail-open)", e);
            return null;
        }
    }

    // 대소문자만 바꿔 카운터를 갈아타는 우회 차단
    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String failKey(String email) {
        return "login:fail:" + email;
    }

    private String lockKey(String email) {
        return "login:lock:" + email;
    }
}
