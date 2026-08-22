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
}
