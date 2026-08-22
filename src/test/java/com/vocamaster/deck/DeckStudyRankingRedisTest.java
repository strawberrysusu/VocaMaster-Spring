package com.vocamaster.deck;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * study 항의 Redis 사본 검증 — DeckStudyRankingListenerTest(랭킹 off, DB만)가 못 보던 구간 (Codex 검산 2026-08-22).
 * ① 비공개 대상은 ZSET 멤버가 생기면 안 됨 (ZINCRBY는 멤버를 만든다)
 * ② PUBLIC 대상은 커밋 후 +1
 * ③ 비공개→공개 전환 점수에 study 항 포함
 */
@TestPropertySource(properties = "ranking.popular.enabled=true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeckStudyRankingRedisTest extends AbstractIntegrationTest {

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

    @Autowired private DeckRankingService rankingService;
    @Autowired private DeckService deckService;
    @Autowired private StatsService statsService;
    @Autowired private DeckRepository deckRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DailyUserStatRepository dailyUserStatRepository;
    @Autowired private StringRedisTemplate redis;
    @Autowired private PlatformTransactionManager txManager;

    private User owner;
    private User learner;

    @BeforeEach
    void setUp() {
        redis.delete(DeckRankingService.KEY);
        redis.delete(DeckRankingService.READY_KEY);
        long tag = System.nanoTime();
        owner = userRepository.save(User.builder()
                .email("ro_" + tag + "@test.com").password("encoded").nickname("원작자").build());
        learner = userRepository.save(User.builder()
                .email("rl_" + tag + "@test.com").password("encoded").nickname("학습자").build());
    }

    @AfterEach
    void cleanUp() {
        for (User u : List.of(learner, owner)) {
            for (Deck d : deckRepository.findByUserIdOrderByCreatedAtDesc(u.getId())) {
                cardRepository.deleteAll(cardRepository.findByDeckId(d.getId()));
                deckRepository.deleteById(d.getId());
            }
        }
        for (User u : List.of(learner, owner)) {
            dailyUserStatRepository.findByUserIdAndStatDate(u.getId(), LocalDate.now(KST))
                    .ifPresent(dailyUserStatRepository::delete);
            userRepository.delete(u);
        }
        redis.delete(DeckRankingService.KEY);
        redis.delete(DeckRankingService.READY_KEY);
    }

    private Deck newRoot(DeckVisibility visibility) {
        Deck root = deckRepository.save(Deck.builder()
                .title("redis " + System.nanoTime()).visibility(visibility).user(owner).build());
        cardRepository.save(Card.builder().front("zset").back("순위표").deck(root).build());
        return root;
    }

    private Double score(Deck d) {
        return redis.opsForZSet().score(DeckRankingService.KEY, String.valueOf(d.getId()));
    }

    @Test
    @DisplayName("UNLISTED 원본: 타인이 복사·학습해도 ZSET 멤버가 생기지 않는다 (DB 카운트는 쌓임)")
    void nonPublicTarget_noRedisMember() {
        Deck root = newRoot(DeckVisibility.UNLISTED);
        rankingService.topDeckIds(0, 50);                                 // 재구축 → ready 표지
        Long copyId = deckService.copy(root.getId(), learner.getId()).getId();
        statsService.recordStudy(learner.getId(), copyId);

        Deck fresh = deckRepository.findById(root.getId()).orElseThrow();
        assertEquals(1, fresh.getCopyCount(), "DB 복사 카운트는 사실로 쌓임");
        assertEquals(1, fresh.getStudyCount(), "DB study 카운트도");
        assertNull(score(root), "비공개 대상은 순위표 사본에 멤버로 만들어지면 안 됨 (ZINCRBY 오염 방지)");
    }

    @Test
    @DisplayName("PUBLIC 원본: 복사 +3, 학습 +1 — 커밋 후 ZSET에 반영, 같은 날 재학습은 +0")
    void publicTarget_incrementsAfterCommit() {
        Deck root = newRoot(DeckVisibility.PUBLIC);
        rankingService.topDeckIds(0, 50);
        assertEquals(0.0, score(root), "재구축 직후 0점 멤버");

        Long copyId = deckService.copy(root.getId(), learner.getId()).getId();
        assertEquals(3.0, score(root), "복사 +3");

        statsService.recordStudy(learner.getId(), copyId);
        assertEquals(4.0, score(root), "학습 +1 (REQUIRES_NEW 커밋 후)");

        statsService.recordStudy(learner.getId(), copyId);
        assertEquals(4.0, score(root), "같은 날 재학습은 Redis에도 +0");
    }

    @Test
    @DisplayName("학습 트랜잭션 롤백 → AFTER_COMMIT 리스너 미호출 → ZSET도 DB도 불변")
    void rollback_noRedisIncrement() {
        Deck root = newRoot(DeckVisibility.PUBLIC);
        rankingService.topDeckIds(0, 50);
        Long copyId = deckService.copy(root.getId(), learner.getId()).getId();
        assertEquals(3.0, score(root), "전제: 복사 +3");

        new TransactionTemplate(txManager).execute(status -> {
            statsService.recordStudy(learner.getId(), copyId);
            status.setRollbackOnly();
            return null;
        });

        assertEquals(3.0, score(root), "롤백이면 +1 없음 — 즉시 실행 리스너였다면 4.0");
        assertEquals(0, deckRepository.findById(root.getId()).orElseThrow().getStudyCount(), "DB도 0");
    }

    @Test
    @DisplayName("비공개→공개 전환: 등재 점수에 study 항 포함 (copy 3 + study 1 = 4)")
    void becamePublic_includesStudyScore() {
        Deck root = newRoot(DeckVisibility.UNLISTED);
        rankingService.topDeckIds(0, 50);
        Long copyId = deckService.copy(root.getId(), learner.getId()).getId();
        statsService.recordStudy(learner.getId(), copyId);
        assertNull(score(root), "전제: 아직 비공개라 멤버 없음");

        deckService.updateVisibility(root.getId(), owner.getId(), DeckVisibility.PUBLIC);
        assertEquals(4.0, score(root), "예전 공식(like·copy만)이면 3.0 — study 항 누락 버그");
    }
}
