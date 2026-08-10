package com.vocamaster.deck;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.deck.dto.DeckResponse;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 동시 복사 검증 — copy_count 원자적 UPDATE (ADR-031).
 *
 * ReviewServiceConcurrencyTest의 '트랜잭션 2개 인터리빙' 패턴을 안 쓰는 이유:
 * 원자적 UPDATE는 행 잠금이라, 트랜잭션 A 안에서 B를 실행하면 B가 A의 잠금을
 * 기다리고 A는 B의 종료를 기다리는 교착이 됨 (낙관적 락은 읽기가 잠그지 않아 가능했던 패턴).
 * → 진짜 스레드 2개로 실행. 타이밍과 무관하게 결과는 결정적: 둘 다 성공 + 카운트 정확히 2.
 *
 * 자동 롤백을 끄는 이유는 리뷰 테스트와 동일 — 스레드의 트랜잭션은 커밋된 데이터만 봄.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeckCopyConcurrencyTest extends AbstractIntegrationTest {

    @Autowired private DeckService deckService;
    @Autowired private DeckRepository deckRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private UserRepository userRepository;

    private User owner;
    private User copierA;
    private User copierB;
    private Deck source;

    @BeforeEach
    void setUp() {
        long tag = System.nanoTime();   // 컨테이너 재사용 대비 유니크 이메일
        owner = userRepository.save(User.builder()
                .email("owner_" + tag + "@test.com").password("encoded").nickname("원본주인").build());
        copierA = userRepository.save(User.builder()
                .email("a_" + tag + "@test.com").password("encoded").nickname("복사자A").build());
        copierB = userRepository.save(User.builder()
                .email("b_" + tag + "@test.com").password("encoded").nickname("복사자B").build());
        source = deckRepository.save(Deck.builder()
                .title("인기덱").visibility(DeckVisibility.PUBLIC).user(owner).build());
        cardRepository.save(Card.builder().front("race").back("경쟁 상태").deck(source).build());
    }

    @AfterEach
    void cleanUp() {
        // FK 역순 '명시' 삭제 — 세션 밖 detached 엔티티의 cascade는 동작이 미묘해서 안 기댐 (실제로 FK에 걸렸음)
        List<Deck> decksToRemove = new ArrayList<>();
        decksToRemove.addAll(deckRepository.findByUserIdOrderByCreatedAtDesc(copierA.getId()));
        decksToRemove.addAll(deckRepository.findByUserIdOrderByCreatedAtDesc(copierB.getId()));
        decksToRemove.add(source);
        for (Deck d : decksToRemove) {
            cardRepository.deleteAll(cardRepository.findByDeckId(d.getId()));
            deckRepository.deleteById(d.getId());
        }
        userRepository.delete(copierA);
        userRepository.delete(copierB);
        userRepository.delete(owner);
    }

    @Test
    @DisplayName("동시 복사 2건 — lost update 없이 copy_count 정확히 2 (원자적 UPDATE)")
    void concurrentCopy_bothIncrementsSurvive() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<DeckResponse> copyAsA = () -> {
            ready.countDown();
            start.await();
            return deckService.copy(source.getId(), copierA.getId());
        };
        Callable<DeckResponse> copyAsB = () -> {
            ready.countDown();
            start.await();
            return deckService.copy(source.getId(), copierB.getId());
        };

        Future<DeckResponse> futureA = pool.submit(copyAsA);
        Future<DeckResponse> futureB = pool.submit(copyAsB);
        ready.await();
        start.countDown();   // 두 스레드 동시 출발

        DeckResponse a = futureA.get(30, TimeUnit.SECONDS);   // 스레드 안 예외는 여기서 재던져짐
        DeckResponse b = futureB.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertNotEquals(a.getId(), b.getId(), "복사본은 각자 별개의 덱");
        assertEquals(2, deckRepository.findById(source.getId()).orElseThrow().getCopyCount(),
                "read-modify-write였다면 여기가 1이 될 수 있음 — 원자적 UPDATE라 2");
        assertEquals(1, cardRepository.findByDeckId(a.getId()).size());
        assertEquals(1, cardRepository.findByDeckId(b.getId()).size());
    }
}
