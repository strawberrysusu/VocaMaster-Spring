package com.vocamaster.stats;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class StatsServiceTest extends AbstractIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @Autowired private StatsService statsService;
    @Autowired private DailyUserStatRepository dailyUserStatRepository;
    @Autowired private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("stats@test.com")
                .password("encoded")
                .nickname("출석왕")
                .build());
    }

    @Test
    @DisplayName("생애 첫 학습 (어제 기록 없음) → 오늘 줄 생성, streak 1")
    void recordStudy_firstEver_startsAtOne() {
        statsService.recordStudy(user.getId(), null);   // deckId null = 랭킹 구독자 skip (출석만 검증)

        DailyUserStat stat = dailyUserStatRepository
                .findByUserIdAndStatDate(user.getId(), TODAY).orElseThrow();
        assertEquals(1, stat.getStreak());
        assertEquals(1, stat.getStudyCount());
    }

    @Test
    @DisplayName("어제도 학습함 → 오늘 streak = 어제 + 1")
    void recordStudy_consecutive_incrementsStreak() {
        saveStat(TODAY.minusDays(1), 5, 3);     // 어제: streak 3

        statsService.recordStudy(user.getId(), null);   // deckId null = 랭킹 구독자 skip (출석만 검증)

        DailyUserStat todayStat = dailyUserStatRepository
                .findByUserIdAndStatDate(user.getId(), TODAY).orElseThrow();
        assertEquals(4, todayStat.getStreak(), "어제 3 → 오늘 4");
        assertEquals(1, todayStat.getStudyCount());
    }

    @Test
    @DisplayName("그제만 있고 어제 없음 (연속 끊김) → 오늘 streak 1로 리셋")
    void recordStudy_gap_resetsToOne() {
        saveStat(TODAY.minusDays(2), 5, 7);     // 그제: streak 7, 어제는 쉼

        statsService.recordStudy(user.getId(), null);   // deckId null = 랭킹 구독자 skip (출석만 검증)

        DailyUserStat todayStat = dailyUserStatRepository
                .findByUserIdAndStatDate(user.getId(), TODAY).orElseThrow();
        assertEquals(1, todayStat.getStreak(), "하루 쉬면 연속은 처음부터");
    }

    @Test
    @DisplayName("같은 날 두 번째 학습 → studyCount만 +1, streak은 그대로")
    void recordStudy_sameDay_incrementsCountOnly() {
        saveStat(TODAY.minusDays(1), 2, 3);     // 어제: streak 3
        statsService.recordStudy(user.getId(), null);   // deckId null = 랭킹 구독자 skip (출석만 검증)     // 오늘 첫 학습 → streak 4
        statsService.recordStudy(user.getId(), null);   // deckId null = 랭킹 구독자 skip (출석만 검증)     // 오늘 두 번째 → 원자적 +1 경로

        DailyUserStat todayStat = dailyUserStatRepository
                .findByUserIdAndStatDate(user.getId(), TODAY).orElseThrow();
        assertEquals(2, todayStat.getStudyCount(), "횟수만 쌓임");
        assertEquals(4, todayStat.getStreak(), "streak은 하루에 한 번만 정해짐");
    }

    @Test
    @DisplayName("표시 streak (A 정책) — 오늘 학습 전이면 어제까지의 연속을 유지해서 보여줌")
    void getDisplayStreak_todayNotYet_keepsYesterday() {
        saveStat(TODAY.minusDays(1), 3, 5);     // 어제까지 5일 연속, 오늘은 아직

        assertEquals(5, statsService.getDisplayStreak(user.getId()), "오늘 하루는 유예 — 어제 값 유지");
        assertEquals(0, statsService.getTodayStudyCount(user.getId()), "오늘 공부 전이니 활동 수는 0");
    }

    @Test
    @DisplayName("표시 streak — 어제도 안 했으면 끊김 확정 → 0")
    void getDisplayStreak_brokenChain_zero() {
        saveStat(TODAY.minusDays(2), 3, 5);     // 그제까지만 기록, 어제 쉼

        assertEquals(0, statsService.getDisplayStreak(user.getId()));
    }

    @Test
    @DisplayName("upsert 연속 2번 — 줄은 하나만, 두 번째 INSERT는 +1로 흡수 (동시 최초 생성 회귀)")
    void upsertTodayRow_twice_singleRowCountTwo() {
        // "첫 학습 동시 2건이 둘 다 UPDATE 0행을 본" 상황의 후속 동작을 결정적으로 재현
        dailyUserStatRepository.upsertTodayRow(user.getId(), TODAY, 1);
        dailyUserStatRepository.upsertTodayRow(user.getId(), TODAY, 1);

        DailyUserStat stat = dailyUserStatRepository
                .findByUserIdAndStatDate(user.getId(), TODAY).orElseThrow();
        assertEquals(2, stat.getStudyCount(), "두 번째는 INSERT가 아니라 +1로 전환");
        assertEquals(1, stat.getStreak(), "streak은 첫 INSERT 값 유지");
    }

    private void saveStat(LocalDate date, int count, int streak) {
        dailyUserStatRepository.save(DailyUserStat.builder()
                .user(user)
                .statDate(date)
                .studyCount(count)
                .streak(streak)
                .build());
    }

    // ── 통계 화면 overview (2026-08-23) ──
    @Autowired private com.vocamaster.deck.DeckRepository deckRepository;
    @Autowired private com.vocamaster.card.CardRepository cardRepository;
    @Autowired private com.vocamaster.review.CardProgressRepository cardProgressRepository;

    @Test
    @DisplayName("overview — 28일 0 채움·누적 집계·덱별 진행률(시작/숙달)이 GROUP BY 결과와 일치")
    void overview_aggregatesCorrectly() {
        saveStat(TODAY.minusDays(40), 9, 1);      // 창 밖 — days엔 없고 누적엔 포함
        saveStat(TODAY.minusDays(2), 4, 1);
        saveStat(TODAY.minusDays(1), 6, 2);
        saveStat(TODAY, 3, 3);

        var deck = deckRepository.save(com.vocamaster.deck.Deck.builder().title("통계 덱").user(user).build());
        var c1 = cardRepository.save(com.vocamaster.card.Card.builder().front("a").back("1").deck(deck).build());
        var c2 = cardRepository.save(com.vocamaster.card.Card.builder().front("b").back("2").deck(deck).build());
        cardRepository.save(com.vocamaster.card.Card.builder().front("c").back("3").deck(deck).build());   // 아직 안 본 카드
        var now = java.time.LocalDateTime.now();
        cardProgressRepository.save(com.vocamaster.review.CardProgress.builder()
                .user(user).card(c1).boxLevel(2).correctStreak(1).wrongCount(0).nextReviewAt(now).build());
        cardProgressRepository.save(com.vocamaster.review.CardProgress.builder()
                .user(user).card(c2).boxLevel(6).correctStreak(5).wrongCount(0).nextReviewAt(now).build());

        var res = statsService.getOverview(user.getId());

        assertEquals(StatsService.OVERVIEW_DAYS, res.getDays().size(), "빈 날도 0으로 28칸");
        assertEquals(TODAY, res.getDays().get(res.getDays().size() - 1).getDate(), "마지막 칸이 오늘");
        assertEquals(3, res.getDays().get(res.getDays().size() - 1).getStudyCount());
        assertEquals(0, res.getDays().get(0).getStudyCount(), "27일 전은 행 없음 → 0");
        assertEquals(9 + 4 + 6 + 3, res.getTotalStudy(), "누적은 창 밖(40일 전)도 포함");
        assertEquals(3, res.getBestStreak());
        assertEquals(4, res.getActiveDays());
        assertEquals(3, res.getStreak(), "오늘 줄 있으면 오늘 streak");

        var d = res.getDecks().stream().filter(x -> x.getDeckId().equals(deck.getId())).findFirst().orElseThrow();
        assertEquals(3, d.getCardCount());
        assertEquals(2, d.getStarted(), "진행 기록 있는 카드 2장");
        assertEquals(1, d.getMastered(), "박스 5 이상 1장");
        assertTrue(res.getBoxes().stream().anyMatch(b -> b.getBox() == 6 && b.getCount() == 1));
    }
}
