package com.vocamaster.deck;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.deck.dto.PublicDeckResponse;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 인기 랭킹 ZSET 검증 (ADR-035).
 *
 * NOT_SUPPORTED인 이유: afterCommit 훅은 '진짜 커밋'이 있어야 실행된다.
 * 자동 롤백 모드에는 커밋이 없어 훅이 영원히 안 돌아 검증 불가.
 * Redis는 전용 컨테이너 — dev Redis(6379) 오염 방지. 매 테스트 전 랭킹 키 초기화.
 */
@TestPropertySource(properties = "ranking.popular.enabled=true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeckRankingServiceTest extends AbstractIntegrationTest {

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

    @Autowired private DeckRankingService rankingService;
    @Autowired private DeckLikeService deckLikeService;
    @Autowired private DeckService deckService;
    @Autowired private PublicDeckService publicDeckService;
    @Autowired private DeckRepository deckRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private StringRedisTemplate redis;

    private User owner;
    private User liker;
    private Deck a;        // PUBLIC, like2 → 10점
    private Deck b;        // PUBLIC, copy4 → 12점
    private Deck c;        // PUBLIC, 0점
    private Deck hidden;   // PRIVATE, like1·copy1 — 랭킹에 나오면 안 됨

    @BeforeEach
    void setUp() {
        redis.delete(DeckRankingService.KEY);
        redis.delete(DeckRankingService.READY_KEY);

        long tag = System.nanoTime();
        owner = userRepository.save(User.builder()
                .email("rank_o_" + tag + "@test.com").password("encoded").nickname("작가").build());
        liker = userRepository.save(User.builder()
                .email("rank_l_" + tag + "@test.com").password("encoded").nickname("팬").build());
        a = deckRepository.save(Deck.builder().title("rankA " + tag).likeCount(2)
                .visibility(DeckVisibility.PUBLIC).user(owner).build());
        b = deckRepository.save(Deck.builder().title("rankB " + tag).copyCount(4)
                .visibility(DeckVisibility.PUBLIC).user(owner).build());
        c = deckRepository.save(Deck.builder().title("rankC " + tag)
                .visibility(DeckVisibility.PUBLIC).user(owner).build());
        hidden = deckRepository.save(Deck.builder().title("rankHidden " + tag).likeCount(1).copyCount(1)
                .visibility(DeckVisibility.PRIVATE).user(owner).build());
    }

    @AfterEach
    void cleanUp() {
        // 덱 삭제가 deck_likes를 CASCADE로 지움(V12) → 유저 삭제 가능
        for (Deck d : List.of(a, b, c, hidden)) {
            deckRepository.findById(d.getId()).ifPresent(x -> deckRepository.deleteById(x.getId()));
        }
        userRepository.delete(liker);
        userRepository.delete(owner);
        redis.delete(DeckRankingService.KEY);
        redis.delete(DeckRankingService.READY_KEY);
    }

    @Test
    @DisplayName("첫 조회가 재구축 — PUBLIC만, 점수 내림차순 (b12 > a10 > c0)")
    void rebuildOnFirstRead_publicOnlyOrdered() {
        List<Long> ids = rankingService.topDeckIds(0, 50);

        assertNotNull(ids);
        assertTrue(ids.indexOf(b.getId()) < ids.indexOf(a.getId()), "copy4(12) > like2(10)");
        assertTrue(ids.indexOf(a.getId()) < ids.indexOf(c.getId()), "like2(10) > 0");
        assertFalse(ids.contains(hidden.getId()), "PRIVATE은 재구축에서 제외");
        assertTrue(Boolean.TRUE.equals(redis.hasKey(DeckRankingService.READY_KEY)), "ready 표지 생성");
    }

    @Test
    @DisplayName("좋아요 커밋 → 순위 즉시 반영 (a가 15점으로 1위)")
    void likeCommit_movesRank() {
        rankingService.topDeckIds(0, 50);                       // 재구축

        deckLikeService.like(a.getId(), liker.getId());         // 커밋 → afterCommit → +5

        List<Long> ids = rankingService.topDeckIds(0, 50);
        assertEquals(a.getId(), ids.get(0), "10+5=15 > 12");
        assertEquals(15.0, redis.opsForZSet().score(DeckRankingService.KEY, String.valueOf(a.getId())), 1e-6);   // 소수부 = tie-breaker
    }

    @Test
    @DisplayName("ready 표지 없으면 증감 무시 — 덱 하나짜리 가짜 순위표가 안 생김 (Codex ② 회귀)")
    void incrementWithoutReady_skipped() {
        // 재구축 없이 좋아요만 발생 (캐시 만료 직후 상황 재현)
        deckLikeService.like(a.getId(), liker.getId());

        assertNotEquals(Boolean.TRUE, redis.hasKey(DeckRankingService.KEY),
                "표지 없이 ZINCRBY가 실행되면 TTL 없는 불완전 순위표가 생기고 재구축이 영영 안 걸린다");
    }

    @Test
    @DisplayName("낡은 id — 청소(자가 치유) 후 그 요청은 DB 폴백 (페이지 구멍 없음)")
    void staleId_selfHealsAndFallsBack() {
        rankingService.topDeckIds(0, 50);                       // 재구축 (a 포함)

        // 훅을 거치지 않는 직접 변경 = afterCommit 유실(서버 다운 등) 시나리오 재현
        Deck stale = deckRepository.findById(a.getId()).orElseThrow();
        stale.setVisibility(DeckVisibility.PRIVATE);
        deckRepository.save(stale);

        List<Long> resultIds = publicDeckService.search(null, 0, 50, "popular")
                .map(PublicDeckResponse::getId).getContent();

        assertFalse(resultIds.contains(a.getId()), "비공개 전환된 덱은 응답에 없어야 (DB가 최종 판단)");
        assertTrue(resultIds.indexOf(b.getId()) < resultIds.indexOf(c.getId()), "DB 폴백도 인기순 유지");
        assertNull(redis.opsForZSet().score(DeckRankingService.KEY, String.valueOf(a.getId())),
                "발견된 stale id는 ZREM으로 자가 치유");
    }

    @Test
    @DisplayName("공개 전환 훅 — PRIVATE→PUBLIC이면 현재 점수로 등재, PUBLIC→PRIVATE이면 제거")
    void visibilityHooks_updateRanking() {
        rankingService.topDeckIds(0, 50);                       // 재구축

        deckService.updateVisibility(hidden.getId(), owner.getId(), DeckVisibility.PUBLIC);
        assertEquals(8.0, redis.opsForZSet().score(DeckRankingService.KEY, String.valueOf(hidden.getId())), 1e-6,
                "like1×5 + copy1×3 = 8점으로 등재 (소수부 = tie-breaker)");

        deckService.updateVisibility(b.getId(), owner.getId(), DeckVisibility.PRIVATE);
        assertNull(redis.opsForZSet().score(DeckRankingService.KEY, String.valueOf(b.getId())),
                "PUBLIC 이탈은 즉시 ZREM");
    }

    @Test
    @DisplayName("동점 정렬 = DB와 동일(최신 우선) — tie-breaker 소수부. 예전엔 멤버 문자열 사전순이라 어긋남 (Codex 감사)")
    void tieOrder_matchesDb_newestFirst() {
        // c(0점)보다 나중에 만든 0점 덱 — id가 더 큼 = DB 규칙(createdAt desc, id desc)상 위
        Deck newer = deckRepository.save(Deck.builder().title("rankNewer " + System.nanoTime())
                .visibility(DeckVisibility.PUBLIC).user(owner).build());
        try {
            List<Long> ids = rankingService.topDeckIds(0, 500);

            int newerPos = ids.indexOf(newer.getId());
            int cPos = ids.indexOf(c.getId());
            assertTrue(newerPos >= 0 && cPos >= 0, "둘 다 순위표에 있어야");
            assertTrue(newerPos < cPos, "동점(0점)이면 최신(id 큰 쪽)이 위 — DB 정렬과 동일해야");
        } finally {
            deckRepository.deleteById(newer.getId());
        }
    }

    @Test
    @DisplayName("숫자 아닌 멤버가 섞이면 — 500이 아니라 DB 폴백(null) + 자가 치유(키 삭제 → 다음 조회 재구축)")
    void corruptedMember_failsOpenAndSelfHeals() {
        rankingService.topDeckIds(0, 50);                                             // 재구축 → ready
        redis.opsForZSet().add(DeckRankingService.KEY, "corrupted-not-a-number", 999_999);   // 손상 주입

        List<Long> ids = assertDoesNotThrow(() -> rankingService.topDeckIds(0, 50),
                "NumberFormatException이 새어 나가면 인기 목록 전체가 500");
        assertNull(ids, "손상 감지 → DB 폴백");
        assertNotEquals(Boolean.TRUE, redis.hasKey(DeckRankingService.KEY), "자가 치유: 본체 삭제");
        assertNotEquals(Boolean.TRUE, redis.hasKey(DeckRankingService.READY_KEY), "ready 표지도 삭제");

        List<Long> next = rankingService.topDeckIds(0, 50);
        assertNotNull(next, "다음 조회는 재구축된 깨끗한 캐시");
        assertTrue(next.contains(b.getId()));
    }
}
