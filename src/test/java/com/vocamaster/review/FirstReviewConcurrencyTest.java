package com.vocamaster.review;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.stats.DailyUserStatRepository;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 처음 만난 카드의 첫 답변 2건이 동시에 — CardProgress 최초 INSERT가 (user, card) unique에 충돌 (Codex 전수 감사 2026-08-23).
 * 보증: 성적표는 정확히 1장, 패배 쪽은 DataIntegrityViolation(→ GlobalExceptionHandler가 409). 500이 아님.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FirstReviewConcurrencyTest extends AbstractIntegrationTest {

    @Autowired private ReviewService reviewService;
    @Autowired private CardProgressRepository cardProgressRepository;
    @Autowired private DailyUserStatRepository dailyUserStatRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private UserRepository userRepository;

    private User user;
    private Deck deck;
    private Card card;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("first_" + System.nanoTime() + "@test.com").password("encoded").nickname("첫복습").build());
        deck = deckRepository.save(Deck.builder().title("first").user(user).build());
        card = cardRepository.save(Card.builder().front("f").back("b").deck(deck).build());
    }

    @AfterEach
    void cleanUp() {
        deckRepository.deleteById(deck.getId());   // V15 CASCADE — 카드·성적표 함께
        dailyUserStatRepository.findByUserIdAndStatDate(user.getId(), LocalDate.now(ZoneId.of("Asia/Seoul")))
                .ifPresent(dailyUserStatRepository::delete);
        userRepository.delete(user);
    }

    @Test
    @DisplayName("첫 답변 동시 2건 → 성적표 1장, 한쪽은 DataIntegrityViolation(409) — 500 아님")
    void firstAnswer_twiceConcurrently() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger(), conflict = new AtomicInteger(), other = new AtomicInteger();
        List<Future<?>> fs = List.of(
                pool.submit(() -> run(go, ok, conflict, other)),
                pool.submit(() -> run(go, ok, conflict, other)));
        go.countDown();
        for (Future<?> f : fs) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(0, other.get(), "예상 밖 예외(500 후보) 없어야");
        assertEquals(1, ok.get(), "성공은 정확히 1");
        assertTrue(conflict.get() <= 1);
        assertEquals(1, cardProgressRepository.findByUserIdAndCardId(user.getId(), card.getId()).stream().count(), "성적표 1장");
    }

    private void run(CountDownLatch go, AtomicInteger ok, AtomicInteger conflict, AtomicInteger other) {
        try {
            go.await();
            reviewService.recordAnswer(user.getId(), card.getId(), true);
            ok.incrementAndGet();
        } catch (DataIntegrityViolationException e) {
            conflict.incrementAndGet();
        } catch (Exception e) {
            other.incrementAndGet();
            e.printStackTrace();
        }
    }
}
