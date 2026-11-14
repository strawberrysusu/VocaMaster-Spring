package com.vocamaster.ranking;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.stats.DailyUserStat;
import com.vocamaster.stats.DailyUserStatRepository;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 학습 랭킹 (ADR-059) — 답한 카드 1장 = 1점, 오늘 / 전체, 같은 점수 같은 순위, 닉네임 가리기, 탈퇴 제외.
 * 테스트 DB에 다른 테스트의 통계가 남아 있을 수 있어, 여기서 만든 사람들은 아주 큰 점수(900만대)로 맨 위에 둔다.
 */
class StudyRankingServiceTest extends AbstractIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int BIG = 9_000_000;

    @Autowired private StudyRankingService service;
    @Autowired private UserRepository userRepository;
    @Autowired private DailyUserStatRepository statRepository;

    private User top;       // 오늘 1등, 닉네임 가리기 켬
    private User second;    // 오늘 공동 2등
    private User tie;       // 오늘 공동 2등 (나)
    private User gone;      // 탈퇴 — 점수가 커도 안 나온다

    @BeforeEach
    void setUp() {
        LocalDate today = LocalDate.now(KST);
        top = user("qjwkr15", true);
        second = user("민수", false);
        tie = user("현동", false);
        gone = user("탈퇴한사람", false);
        gone.setDeletedAt(LocalDateTime.now());
        userRepository.save(gone);

        stat(top, today, BIG + 3);
        stat(second, today, BIG + 2);
        stat(tie, today, BIG + 2);
        stat(gone, today, BIG + 9);
        stat(second, today.minusDays(1), 10);   // 전체 합계에서는 second가 top을 넘는다 (BIG+12 > BIG+3)
    }

    @Test
    @DisplayName("오늘 — 점수 순, 같은 점수는 같은 순위(1·2·2), 탈퇴 제외, 가린 닉네임은 앞 글자 + ***")
    void today() {
        StudyRankingResponse res = service.ranking(tie.getId(), "today", 50);
        List<StudyRankingResponse.Entry> mine = res.entries().stream().filter(e -> e.points() > BIG).toList();

        assertEquals(3, mine.size(), "탈퇴한 사람은 빠진다");
        assertEquals(new StudyRankingResponse.Entry(1, "qjw***", BIG + 3, false), mine.get(0));
        assertEquals(2, mine.get(1).rank());
        assertEquals(2, mine.get(2).rank(), "같은 점수면 같은 순위");
        assertTrue(res.entries().stream().noneMatch(e -> e.name().equals("qjwkr15")), "가린 사람의 진짜 닉네임은 응답에 없다");
        assertTrue(res.entries().stream().noneMatch(e -> e.name().equals("탈퇴한사람")));

        assertEquals(new StudyRankingResponse.Entry(2, "현동", BIG + 2, true), res.me(), "내 순위 = 나보다 높은 사람 수 + 1");
        assertEquals("today", res.period());
        assertEquals(LocalDate.now(KST), res.day());
    }

    @Test
    @DisplayName("전체 — 지금까지의 합계 순")
    void total() {
        StudyRankingResponse res = service.ranking(top.getId(), "all", 50);
        List<StudyRankingResponse.Entry> mine = res.entries().stream().filter(e -> e.points() > BIG).toList();

        assertEquals("민수", mine.get(0).name());
        assertEquals(BIG + 12, mine.get(0).points());
        assertEquals(new StudyRankingResponse.Entry(2, "qjw***", BIG + 3, true), res.me(), "내 줄도 가린 이름으로 — 남들이 보는 그대로");
        assertNull(res.day());
    }

    @Test
    @DisplayName("점수가 없으면 내 순위는 null, 상위 몇 명만 달라고 하면 그만큼만")
    void noPointsAndLimit() {
        User fresh = user("새사람", false);
        StudyRankingResponse res = service.ranking(fresh.getId(), "today", 1);
        assertNull(res.me());
        assertEquals(1, res.entries().size());
    }

    @Test
    @DisplayName("기간은 today 또는 all만")
    void badPeriod() {
        assertThrows(BadRequestException.class, () -> service.ranking(top.getId(), "week", 50));
    }

    @Test
    @DisplayName("닉네임 가리기 — 길이의 1/3(1~3글자) + ***, 이모지를 반으로 가르지 않는다")
    void mask() {
        assertEquals("qjw***", StudyRankingService.mask("qjwkr15"));
        assertEquals("조***", StudyRankingService.mask("조현동"));
        assertEquals("a***", StudyRankingService.mask("ab"));
        assertEquals("Hyu***", StudyRankingService.mask("Hyundong Cho"));
        assertEquals("😀😀***", StudyRankingService.mask("😀😀😀😀"));
        assertEquals("***", StudyRankingService.mask(""));
    }

    private User user(String nickname, boolean masked) {
        return userRepository.save(User.builder()
                .email("rank_" + System.nanoTime() + "@test.com").password("x").nickname(nickname).rankingMasked(masked).build());
    }

    private void stat(User u, LocalDate day, int count) {
        statRepository.save(DailyUserStat.builder().user(u).statDate(day).studyCount(count).streak(1).build());
    }
}
