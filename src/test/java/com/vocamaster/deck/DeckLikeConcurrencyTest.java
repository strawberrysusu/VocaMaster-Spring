package com.vocamaster.deck;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.deck.dto.LikeResponse;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 동시 좋아요 검증 (ADR-032) — 복사(ADR-031)와 동일한 교착 구조의 회귀 테스트.
 * deck_likes INSERT의 FK S락 + like_count UPDATE X락 조합 → 카운트 X락을 먼저 잡는
 * 순서가 무너지면 이 테스트가 데드락으로 빨개진다.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeckLikeConcurrencyTest extends AbstractIntegrationTest {

    @Autowired private DeckLikeService deckLikeService;
    @Autowired private DeckLikeRepository deckLikeRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private UserRepository userRepository;

    private User owner;
    private User likerA;
    private User likerB;
    private Deck deck;

    @BeforeEach
    void setUp() {
        long tag = System.nanoTime();
        owner = userRepository.save(User.builder()
                .email("lo_" + tag + "@test.com").password("encoded").nickname("주인").build());
        likerA = userRepository.save(User.builder()
                .email("la_" + tag + "@test.com").password("encoded").nickname("팬A").build());
        likerB = userRepository.save(User.builder()
                .email("lb_" + tag + "@test.com").password("encoded").nickname("팬B").build());
        deck = deckRepository.save(Deck.builder()
                .title("인기덱").visibility(DeckVisibility.PUBLIC).user(owner).build());
    }

    @AfterEach
    void cleanUp() {
        deckLikeRepository.deleteByUserIdAndDeckId(likerA.getId(), deck.getId());
        deckLikeRepository.deleteByUserIdAndDeckId(likerB.getId(), deck.getId());
        deckRepository.deleteById(deck.getId());
        userRepository.delete(likerA);
        userRepository.delete(likerB);
        userRepository.delete(owner);
    }

    @Test
    @DisplayName("같은 유저 동시 더블탭 — unique 위반 경로 포함, 최종은 행 1개·카운트 1 (멱등)")
    void concurrentDoubleTap_sameUser_stillOne() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        // 컨트롤러의 레이스 변환(catch → currentState)을 그대로 재현.
        // 타이밍에 따라 (a) 늦은 쪽이 빠른 경로를 타거나 (b) unique 위반→롤백→재조회 —
        // 어느 경로든 결과는 같아야 한다는 것이 멱등의 정의
        Callable<LikeResponse> tap = () -> {
            ready.countDown();
            start.await();
            try {
                return deckLikeService.like(deck.getId(), likerA.getId());
            } catch (org.springframework.dao.DataIntegrityViolationException race) {
                return deckLikeService.currentState(deck.getId(), likerA.getId());
            }
        };

        Future<LikeResponse> first = pool.submit(tap);
        Future<LikeResponse> second = pool.submit(tap);
        ready.await();
        start.countDown();

        LikeResponse r1 = first.get(30, TimeUnit.SECONDS);
        LikeResponse r2 = second.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(r1.isLiked());
        assertTrue(r2.isLiked());
        assertEquals(1, deckRepository.findById(deck.getId()).orElseThrow().getLikeCount(),
                "unique 위반 롤백이 선행 증가까지 되돌리지 못하면 여기가 2가 된다");
        assertEquals(1, deckLikeRepository.findAll().stream()
                .filter(l -> l.getDeck().getId().equals(deck.getId())).count());
    }

    @Test
    @DisplayName("두 유저 동시 좋아요 — 데드락 없이 카운트 정확히 2")
    void concurrentLike_bothCounted() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<LikeResponse> likeAsA = () -> {
            ready.countDown();
            start.await();
            return deckLikeService.like(deck.getId(), likerA.getId());
        };
        Callable<LikeResponse> likeAsB = () -> {
            ready.countDown();
            start.await();
            return deckLikeService.like(deck.getId(), likerB.getId());
        };

        Future<LikeResponse> futureA = pool.submit(likeAsA);
        Future<LikeResponse> futureB = pool.submit(likeAsB);
        ready.await();
        start.countDown();

        futureA.get(30, TimeUnit.SECONDS);
        futureB.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(2, deckRepository.findById(deck.getId()).orElseThrow().getLikeCount(),
                "S→X 승급 교착이 재발하면 여기 도달 전에 CannotAcquireLock으로 터진다");
        assertTrue(deckLikeRepository.existsByUserIdAndDeckId(likerA.getId(), deck.getId()));
        assertTrue(deckLikeRepository.existsByUserIdAndDeckId(likerB.getId(), deck.getId()));
    }
}
