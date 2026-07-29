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
        statsService.recordStudy(user.getId());

        DailyUserStat stat = dailyUserStatRepository
                .findByUserIdAndStatDate(user.getId(), TODAY).orElseThrow();
        assertEquals(1, stat.getStreak());
        assertEquals(1, stat.getStudyCount());
    }

    @Test
    @DisplayName("어제도 학습함 → 오늘 streak = 어제 + 1")
    void recordStudy_consecutive_incrementsStreak() {
        saveStat(TODAY.minusDays(1), 5, 3);     // 어제: streak 3

        statsService.recordStudy(user.getId());

        DailyUserStat todayStat = dailyUserStatRepository
                .findByUserIdAndStatDate(user.getId(), TODAY).orElseThrow();
        assertEquals(4, todayStat.getStreak(), "어제 3 → 오늘 4");
        assertEquals(1, todayStat.getStudyCount());
    }

    @Test
    @DisplayName("그제만 있고 어제 없음 (연속 끊김) → 오늘 streak 1로 리셋")
    void recordStudy_gap_resetsToOne() {
        saveStat(TODAY.minusDays(2), 5, 7);     // 그제: streak 7, 어제는 쉼

        statsService.recordStudy(user.getId());

        DailyUserStat todayStat = dailyUserStatRepository
                .findByUserIdAndStatDate(user.getId(), TODAY).orElseThrow();
        assertEquals(1, todayStat.getStreak(), "하루 쉬면 연속은 처음부터");
    }

    @Test
    @DisplayName("같은 날 두 번째 학습 → studyCount만 +1, streak은 그대로")
    void recordStudy_sameDay_incrementsCountOnly() {
        saveStat(TODAY.minusDays(1), 2, 3);     // 어제: streak 3
        statsService.recordStudy(user.getId());     // 오늘 첫 학습 → streak 4
        statsService.recordStudy(user.getId());     // 오늘 두 번째 → 원자적 +1 경로

        DailyUserStat todayStat = dailyUserStatRepository
                .findByUserIdAndStatDate(user.getId(), TODAY).orElseThrow();
        assertEquals(2, todayStat.getStudyCount(), "횟수만 쌓임");
        assertEquals(4, todayStat.getStreak(), "streak은 하루에 한 번만 정해짐");
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
