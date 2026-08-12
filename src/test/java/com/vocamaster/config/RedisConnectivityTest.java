package com.vocamaster.config;

import com.vocamaster.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 스프링 ↔ Redis 실연결 검증 (Phase 5 인프라의 마지막 조각).
 *
 * redis-cli 스모크는 "컨테이너가 정상"까지만 증명한다 — 이 테스트가 증명하는 것:
 * 1) yml 설정으로 Lettuce 커넥션이 실제로 붙는가
 * 2) RedisConfig의 JSON 직렬화가 왕복(쓰고 → 되읽기) 가능한가
 * 3) rate limit의 기초인 원자적 INCR이 동작하는가
 *
 * Redis 전용 컨테이너 모듈 없이 GenericContainer로 충분 (포트 6379 하나뿐이라).
 */
class RedisConnectivityTest extends AbstractIntegrationTest {

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

    @Autowired private StringRedisTemplate stringRedisTemplate;          // 카운터용 (자동 제공 빈)
    @Autowired private RedisTemplate<String, Object> redisTemplate;      // JSON 객체용 (RedisConfig 빈)

    @Test
    @DisplayName("문자열 SET/GET + TTL — yml 설정으로 실제 연결됨")
    void stringRoundTripWithTtl() {
        String key = "smoke:string:" + System.nanoTime();

        stringRedisTemplate.opsForValue().set(key, "phase5", Duration.ofSeconds(60));

        assertEquals("phase5", stringRedisTemplate.opsForValue().get(key));
        Long ttl = stringRedisTemplate.getExpire(key);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 60, "모든 키에 TTL — 컨벤션 규칙이 실제로 걸리는지");
        stringRedisTemplate.delete(key);
    }

    @Test
    @DisplayName("객체 JSON 왕복 — RedisConfig 직렬화 검증")
    void objectJsonRoundTrip() {
        String key = "smoke:obj:" + System.nanoTime();
        // List.of(...)는 금지 — JDK 내부 불변 클래스(final)라 @class 타입 정보가 안 실려
        // 되읽기가 깨진다 (이 테스트가 실제로 잡아낸 함정 → redis-conventions.md 규칙화)
        List<String> value = new ArrayList<>(List.of("ubiquitous", "meticulous"));

        redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(60));
        Object read = redisTemplate.opsForValue().get(key);

        assertEquals(value, read);
        redisTemplate.delete(key);
    }

    @Test
    @DisplayName("원자적 INCR — rate limit 카운터의 기초 (1→2 연속 증가)")
    void atomicIncrement() {
        String key = "smoke:incr:" + System.nanoTime();

        assertEquals(1L, stringRedisTemplate.opsForValue().increment(key));
        assertEquals(2L, stringRedisTemplate.opsForValue().increment(key));
        stringRedisTemplate.delete(key);
    }
}
