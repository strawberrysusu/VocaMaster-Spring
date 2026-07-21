package com.vocamaster.review;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.common.exception.ForbiddenException;
import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.review.dto.ReviewAnswerResponse;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ReviewServiceTest extends AbstractIntegrationTest {

    @Autowired private ReviewService reviewService;
    @Autowired private CardProgressRepository cardProgressRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private CardRepository cardRepository;

    @PersistenceContext
    private EntityManager em;

    private User user;
    private Deck deck;
    private Card card;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("review@test.com")
                .password("encoded")
                .nickname("reviewer")
                .build());
        deck = deckRepository.save(Deck.builder()
                .title("Review Deck")
                .user(user)
                .build());
        card = cardRepository.save(Card.builder()
                .front("apple")
                .back("사과")
                .deck(deck)
                .build());
    }

    @Test
    @DisplayName("처음 만난 카드 → progress 자동 생성, DB에 진짜 저장되는지 확인")
    void recordAnswer_firstMeet_createsProgress() {
        // 답변 전에는 성적표가 없다
        assertTrue(cardProgressRepository.findByUserIdAndCardId(user.getId(), card.getId()).isEmpty());

        reviewService.recordAnswer(user.getId(), card.getId(), true);

        // 출석부(영속성 컨텍스트)를 비우고 DB에서 다시 조회 — save 증발 버그를 잡는 검증
        em.flush();
        em.clear();

        CardProgress saved = cardProgressRepository.findByUserIdAndCardId(user.getId(), card.getId())
                .orElseThrow();
        assertEquals(2, saved.getBoxLevel(), "box 1로 생성 → 바로 정답이라 2로 승급");
        assertEquals(1, saved.getCorrectStreak());
        assertEquals(0, saved.getWrongCount());
    }

    @Test
    @DisplayName("맞힘 → box +1, 다음 복습은 '새' 박스의 간격(3일)으로 예약")
    void recordAnswer_correct_promotesBox() {
        reviewService.recordAnswer(user.getId(), card.getId(), true);                                // 생성(1) → 2

        LocalDateTime before = LocalDateTime.now();
        ReviewAnswerResponse res = reviewService.recordAnswer(user.getId(), card.getId(), true);     // 2 → 3
        LocalDateTime after = LocalDateTime.now();

        assertEquals(3, res.getBoxLevel());
        assertEquals(2, res.getCorrectStreak());
        // 호출직전+3일 ≤ nextReviewAt ≤ 호출직후+3일 — Clock 미주입이어도 안정적.
        // "미래인가"만 보면 옛 박스(2)의 간격 1일이 찍히는 순서 버그도 통과해버림
        assertFalse(res.getNextReviewAt().isBefore(before.plusDays(3)),
                "3일보다 이르면 옛 박스 간격이 박제된 순서 버그");
        assertFalse(res.getNextReviewAt().isAfter(after.plusDays(3)),
                "3일보다 늦으면 간격 계산 오류");
    }

    @Test
    @DisplayName("틀림 → box 1 풀 리셋 + streak 0 + wrongCount 누적 + 10분 뒤 재등장")
    void recordAnswer_wrong_resetsToBoxOne() {
        reviewService.recordAnswer(user.getId(), card.getId(), true);                                // → 2
        reviewService.recordAnswer(user.getId(), card.getId(), true);                                // → 3

        LocalDateTime before = LocalDateTime.now();
        ReviewAnswerResponse res = reviewService.recordAnswer(user.getId(), card.getId(), false);    // 틀림
        LocalDateTime after = LocalDateTime.now();

        assertEquals(1, res.getBoxLevel(), "몇 번 박스에 있었든 틀리면 무조건 1");
        assertEquals(0, res.getCorrectStreak(), "연속은 한 번 끊기면 0");
        assertEquals(1, res.getWrongCount(), "누적 오답은 지워지지 않고 쌓임");
        // 틀린 카드는 box 1 간격(10분) 뒤에 바로 재등장해야 함
        assertFalse(res.getNextReviewAt().isBefore(before.plusMinutes(10)),
                "10분보다 이르면 간격 계산 오류");
        assertFalse(res.getNextReviewAt().isAfter(after.plusMinutes(10)),
                "10분보다 늦으면 옛 박스(3)의 간격이 박제된 순서 버그");
    }

    @Test
    @DisplayName("box 6에서 계속 맞혀도 6에 머무름 (천장 — 배열 밖으로 안 나감)")
    void recordAnswer_maxBox_staysAtSix() {
        // 10연속 정답 — Math.min이 없다면 box 7 → BOX_INTERVALS[6]에서 터졌을 상황
        for (int i = 0; i < 10; i++) {
            reviewService.recordAnswer(user.getId(), card.getId(), true);
        }

        CardProgress progress = cardProgressRepository.findByUserIdAndCardId(user.getId(), card.getId())
                .orElseThrow();
        assertEquals(6, progress.getBoxLevel());
        assertEquals(10, progress.getCorrectStreak(), "streak은 천장 없이 계속 쌓임");
    }

    @Test
    @DisplayName("남의 카드에 답변 시도 → Forbidden (IDOR 차단) + 성적표도 안 생김")
    void recordAnswer_othersCard_forbidden() {
        User attacker = userRepository.save(User.builder()
                .email("attacker@test.com")
                .password("encoded")
                .nickname("attacker")
                .build());

        assertThrows(ForbiddenException.class,
                () -> reviewService.recordAnswer(attacker.getId(), card.getId(), true));

        assertTrue(cardProgressRepository.findByUserIdAndCardId(attacker.getId(), card.getId()).isEmpty(),
                "검문소에서 막혔으니 남의 카드에 성적표가 생기면 안 됨");
    }

    @Test
    @DisplayName("존재하지 않는 카드에 답변 시도 → NotFound")
    void recordAnswer_missingCard_notFound() {
        assertThrows(NotFoundException.class,
                () -> reviewService.recordAnswer(user.getId(), 999_999L, true));
    }
}
