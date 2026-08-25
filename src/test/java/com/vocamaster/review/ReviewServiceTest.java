package com.vocamaster.review;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.common.exception.ForbiddenException;
import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.review.dto.BoxCountResponse;
import com.vocamaster.review.dto.DueCardResponse;
import com.vocamaster.review.dto.ReviewAnswerResponse;
import com.vocamaster.review.dto.TodaySummaryResponse;
import com.vocamaster.stats.DailyUserStat;
import com.vocamaster.stats.DailyUserStatRepository;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReviewServiceTest extends AbstractIntegrationTest {

    // 서비스의 모든 시간이 KST 고정이라 테스트도 KST — 기본 시간대 now()는 UTC 러너(CI)에서 9시간 어긋나 실패했다 (첫 CI가 잡음)
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired private ReviewService reviewService;
    @Autowired private CardProgressRepository cardProgressRepository;
    @Autowired private DailyUserStatRepository dailyUserStatRepository;
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

        LocalDateTime before = LocalDateTime.now(KST);
        ReviewAnswerResponse res = reviewService.recordAnswer(user.getId(), card.getId(), true);     // 2 → 3
        LocalDateTime after = LocalDateTime.now(KST);

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

        LocalDateTime before = LocalDateTime.now(KST);
        ReviewAnswerResponse res = reviewService.recordAnswer(user.getId(), card.getId(), false);    // 틀림
        LocalDateTime after = LocalDateTime.now(KST);

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
    @DisplayName("today-summary — 숫자 4개가 각자 다른 것을 센다")
    void getTodaySummary_countsFourNumbers() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        // 어제까지 4일 연속이었다고 세팅
        dailyUserStatRepository.save(DailyUserStat.builder()
                .user(user)
                .statDate(now.toLocalDate().minusDays(1))
                .studyCount(3)
                .streak(4)
                .build());

        Card dueCard = cardRepository.save(Card.builder().front("due").back("숙제").deck(deck).build());
        Card futureCard = cardRepository.save(Card.builder().front("future").back("미래").deck(deck).build());
        saveProgress(user, dueCard, 2, now.minusDays(1));       // 복습 시간 지난 카드 (숙제)
        saveProgress(user, futureCard, 3, now.plusDays(1));     // 아직 때 안 된 카드

        reviewService.recordAnswer(user.getId(), card.getId(), true);   // 오늘 apple 1장 복습

        TodaySummaryResponse summary = reviewService.getTodaySummary(user.getId());

        assertEquals(1, summary.getDueCount(), "숙제는 dueCard 1장 (답한 apple은 미래로 밀림)");
        assertEquals(1, summary.getReviewedTodayCount(), "오늘 복습한 카드는 apple 1장");
        assertEquals(1, summary.getStudyCount(), "오늘 답변 1번");
        assertEquals(5, summary.getStreak(), "어제 4일 + 오늘 학습 = 5");
    }

    @Test
    @DisplayName("답변하면 출석 도장도 같이 찍힘 (Streak 배선 확인)")
    void recordAnswer_alsoRecordsDailyStat() {
        reviewService.recordAnswer(user.getId(), card.getId(), true);

        assertTrue(dailyUserStatRepository.findByUserIdAndStatDate(
                        user.getId(), LocalDate.now(ZoneId.of("Asia/Seoul"))).isPresent(),
                "recordAnswer가 statsService.recordStudy를 호출해야 함");
    }

    @Test
    @DisplayName("존재하지 않는 카드에 답변 시도 → NotFound")
    void recordAnswer_missingCard_notFound() {
        assertThrows(NotFoundException.class,
                () -> reviewService.recordAnswer(user.getId(), 999_999L, true));
    }

    @Test
    @DisplayName("due 조회 — 시간 지난 내 카드만, 오래 기다린 순 (새 카드/미래 카드 제외)")
    void getDueCards_returnsOnlyDueOnes() {
        Card banana = cardRepository.save(Card.builder().front("banana").back("바나나").deck(deck).build());
        Card kiwi = cardRepository.save(Card.builder().front("kiwi").back("키위").deck(deck).build());
        cardRepository.save(Card.builder().front("melon").back("멜론").deck(deck).build());   // progress 없음 = 새 카드

        saveProgress(user, card, 2, LocalDateTime.now(KST).minusDays(1));      // apple — 어제부터 due
        saveProgress(user, banana, 3, LocalDateTime.now(KST).plusDays(1));     // 내일 예정 — 제외
        saveProgress(user, kiwi, 1, LocalDateTime.now(KST).minusDays(2));      // 그저께부터 due — 제일 급함

        List<DueCardResponse> due = reviewService.getDueCards(user.getId(), deck.getId());

        assertEquals(2, due.size(), "새 카드(melon)와 미래 카드(banana)는 제외");
        assertEquals(kiwi.getId(), due.get(0).getCardId(), "가장 오래 기다린 카드부터");
        assertEquals(card.getId(), due.get(1).getCardId());
    }

    @Test
    @DisplayName("due 조회 — 다른 사용자의 progress는 안 섞임")
    void getDueCards_isolatedPerUser() {
        User other = userRepository.save(User.builder()
                .email("other@test.com").password("encoded").nickname("other").build());
        Deck otherDeck = deckRepository.save(Deck.builder().title("Other Deck").user(other).build());
        Card otherCard = cardRepository.save(Card.builder().front("grape").back("포도").deck(otherDeck).build());
        saveProgress(other, otherCard, 2, LocalDateTime.now(KST).minusDays(1));    // 남의 due 카드

        saveProgress(user, card, 2, LocalDateTime.now(KST).minusDays(1));          // 내 due 카드

        List<DueCardResponse> due = reviewService.getDueCards(user.getId(), null);  // 전체 덱 조회

        assertEquals(1, due.size());
        assertEquals(card.getId(), due.get(0).getCardId(), "내 progress만 나와야 함");
    }

    @Test
    @DisplayName("남의 덱 deckId로 due 필터 요청 → Forbidden")
    void getDueCards_othersDeckFilter_forbidden() {
        User other = userRepository.save(User.builder()
                .email("other2@test.com").password("encoded").nickname("other2").build());
        Deck otherDeck = deckRepository.save(Deck.builder().title("Other Deck 2").user(other).build());

        assertThrows(ForbiddenException.class,
                () -> reviewService.getDueCards(user.getId(), otherDeck.getId()));
    }

    private void saveProgress(User owner, Card target, int box, LocalDateTime nextReviewAt) {
        cardProgressRepository.save(CardProgress.builder()
                .user(owner)
                .card(target)
                .boxLevel(box)
                .correctStreak(0)
                .wrongCount(0)
                .nextReviewAt(nextReviewAt)
                .build());
    }

    @Test
    @DisplayName("박스 분포 — 카드 없는 박스도 0으로 채워 항상 6칸 (홈 사다리 차트 계약)")
    void boxDistribution_alwaysSixSlotsZeroFilled() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        saveProgress(user, card, 2, now);
        Card second = cardRepository.save(Card.builder().front("banana").back("바나나").deck(deck).build());
        saveProgress(user, second, 2, now);
        Card third = cardRepository.save(Card.builder().front("cherry").back("체리").deck(deck).build());
        saveProgress(user, third, 5, now);

        List<BoxCountResponse> dist = reviewService.getBoxDistribution(user.getId());

        assertEquals(6, dist.size());
        assertEquals(1, dist.get(0).getBox());
        assertEquals(0, dist.get(0).getCount(), "빈 박스 1은 0으로 채워져야");
        assertEquals(2, dist.get(1).getCount(), "박스 2에 두 장");
        assertEquals(1, dist.get(4).getCount(), "박스 5에 한 장");
        assertEquals(0, dist.get(5).getCount());
    }
}
