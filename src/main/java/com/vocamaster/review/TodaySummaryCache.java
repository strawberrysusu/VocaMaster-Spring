package com.vocamaster.review;

import com.vocamaster.review.dto.TodaySummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 오늘 복습 요약 캐시 — cache-aside (Phase 5 세 번째 사용처).
 *
 * TTL 5분 + 학습 시 무효화의 역할 분담:
 * - 무효화(evict): 사용자의 '행동'으로 숫자가 변한 것 — 즉시성
 * - TTL: 아무 행동 없어도 시간이 흘러 dueCount가 변하는 것 — 이 변화엔 이벤트가 없어 TTL만이 잡음
 * 키에 KST 날짜 포함 — 자정을 넘기면 키 자체가 바뀌어 어제 캐시가 오늘을 오염 못 함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TodaySummaryCache {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${cache.review-summary.enabled:true}")
    private boolean enabled;

    /** null = 미스/장애/손상 전부 — 호출자가 DB 계산 (fail-open) */
    public TodaySummaryResponse get(Long userId, LocalDate date) {
        if (!enabled) return null;
        try {
            return (TodaySummaryResponse) redisTemplate.opsForValue().get(key(userId, date));
        } catch (RuntimeException e) {
            // 연결 실패(DataAccessException)만이 아니라 손상된 값의 역직렬화 실패(SerializationException),
            // 타입 불일치(ClassCastException)까지 — 캐시 읽기는 어떤 실패든 '미스'로 취급해야 기능이 산다.
            // 의도적 광범위 catch (좁히면 손상 케이스가 500으로 샘)
            log.warn("요약 캐시 조회 실패 — DB 계산으로 대체 (fail-open)", e);
            return null;
        }
    }

    public void put(Long userId, LocalDate date, TodaySummaryResponse summary) {
        if (!enabled) return;
        try {
            redisTemplate.opsForValue().set(key(userId, date), summary, TTL);
        } catch (RuntimeException e) {
            log.warn("요약 캐시 저장 실패 — 다음 조회도 DB 계산 (fail-open)", e);
        }
    }

    /**
     * 즉시 삭제. "커밋 확정 후"라는 타이밍은 이제 호출자(TodaySummaryCacheListener의 AFTER_COMMIT)가 책임진다.
     * Phase 5에선 여기서 TransactionSynchronization을 손으로 등록했는데(evictAfterCommit),
     * Phase 6에서 스프링 표준 @TransactionalEventListener로 교체 — 같은 의미, 결합은 제거 (ADR-037).
     */
    public void evict(Long userId, LocalDate date) {
        if (!enabled) return;
        try {
            redisTemplate.delete(key(userId, date));
        } catch (RuntimeException e) {
            log.warn("요약 캐시 삭제 실패 — TTL 5분이 상한선 (fail-open)", e);
        }
    }

    private String key(Long userId, LocalDate date) {
        return "review:summary:" + userId + ":" + date.format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
