package com.vocamaster.review;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.review.dto.TodaySummaryResponse;
import com.vocamaster.stats.DailyUserStatRepository;
import com.vocamaster.stats.StatsService;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 오늘 복습 요약 캐시 검증 (ADR-036).
 *
 * NOT_SUPPORTED: recordStudy의 afterCommit 무효화는 진짜 커밋이 있어야 실행됨.
 * "낡은 값이 그대로 나온다"는 단언이 곧 '캐시 히트'의 증명이라는 점이 이 테스트의 재미.
 */
@TestPropertySource(properties = "cache.review-summary.enabled=true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TodaySummaryCacheTest extends AbstractIntegrationTest {

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

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired private ReviewService reviewService;
    @Autowired private StatsService statsService;
    @Autowired private CardProgressRepository cardProgressRepository;
    @Autowired private DailyUserStatRepository dailyUserStatRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private StringRedisTemplate stringRedis;

    private User user;
    private Deck deck;
    private Card card;

    @BeforeEach
    void setUp() {
        long tag = System.nanoTime();
        user = userRepository.save(User.builder()
                .email("summary_" + tag + "@test.com").password("encoded").nickname("요약러").build());
        deck = deckRepository.save(Deck.builder().title("summary " + tag).user(user).build());
        card = cardRepository.save(Card.builder().front("cache").back("캐시").deck(deck).build());
        stringRedis.delete(cacheKey());
    }

    @AfterEach
    void cleanUp() {
        cardProgressRepository.findByUserIdAndCardId(user.getId(), card.getId())
                .ifPresent(cardProgressRepository::delete);
        dailyUserStatRepository.findByUserIdAndStatDate(user.getId(), LocalDate.now(KST))
                .ifPresent(dailyUserStatRepository::delete);
        cardRepository.delete(card);
        deckRepository.delete(deck);
        userRepository.delete(user);
        stringRedis.delete(cacheKey());
    }

    @Test
    @DisplayName("cache-aside 전체 여정 — 미스→저장→히트(낡은 값=히트의 증거)→학습 시 무효화→재계산")
    void cacheAside_fullJourney() {
        // 1) 미스 → 계산 → 캐싱. DTO 왕복 검증: 두 번째 응답은 Redis에서 온 것
        TodaySummaryResponse first = reviewService.getTodaySummary(user.getId());
        assertEquals(0, first.getDueCount());
        assertTrue(Boolean.TRUE.equals(stringRedis.hasKey(cacheKey())), "미스 후 캐시가 저장돼야");

        TodaySummaryResponse second = reviewService.getTodaySummary(user.getId());
        assertEquals(first.getDueCount(), second.getDueCount());
        assertEquals(first.getStudyCount(), second.getStudyCount());
        assertEquals(first.getStreak(), second.getStreak());          // 4필드 왕복 무결

        // 2) DB를 직접 바꿔도(복습 카드 1장 발생) 캐시는 옛 값 — 낡음이 곧 히트의 증명
        cardProgressRepository.save(CardProgress.builder()
                .user(user).card(card).boxLevel(1).correctStreak(0).wrongCount(0)
                .nextReviewAt(LocalDateTime.now(KST).minusMinutes(1)).build());

        TodaySummaryResponse stale = reviewService.getTodaySummary(user.getId());
        assertEquals(0, stale.getDueCount(), "DB엔 due 1인데 0이 나오면 = 캐시에서 읽었다는 증거");

        // 3) 학습 기록(커밋) → afterCommit 무효화 → 다음 조회는 재계산
        statsService.recordStudy(user.getId());
        assertNotEquals(Boolean.TRUE, stringRedis.hasKey(cacheKey()), "커밋 후 캐시가 지워져야");

        TodaySummaryResponse fresh = reviewService.getTodaySummary(user.getId());
        assertEquals(1, fresh.getDueCount(), "무효화 후엔 DB의 진짜 숫자");
        assertEquals(1, fresh.getStudyCount(), "recordStudy 반영");
    }

    @Test
    @DisplayName("손상된 캐시 값 — 500 없이 DB 계산으로 대체 (역직렬화 실패도 fail-open)")
    void corruptedValue_failsOpen() {
        // JSON 템플릿이 못 읽는 평문을 같은 키에 심어 손상 재현
        stringRedis.opsForValue().set(cacheKey(), "corrupted-not-json");

        TodaySummaryResponse res = assertDoesNotThrow(() -> reviewService.getTodaySummary(user.getId()),
                "손상된 캐시가 현황판 500으로 번지면 안 됨");
        assertEquals(0, res.getDueCount(), "DB 계산 결과가 나와야");
    }

    private String cacheKey() {
        return "review:summary:" + user.getId() + ":"
                + LocalDate.now(KST).format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
