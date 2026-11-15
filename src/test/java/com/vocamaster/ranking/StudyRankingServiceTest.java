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
 * 학습 랭킹 (ADR-059) — 답한 카드 1장 = 1점(하루 상한까지), 오늘 / 전체, 같은 점수 같은 순위, 닉네임 가리기, 탈퇴 제외.
 *
 * <p>다른 테스트가 커밋해 둔 통계가 남아 있을 수 있어 시작할 때 통계 표를 비운다 — 이 테스트의 트랜잭션 안에서만 비워지고
 * 끝나면 되돌아간다(롤백). 하루 상한이 생기기 전에는 900만대 점수로 맨 위에 두었는데, 상한이 있으면 그 방법은 쓸 수 없다 (11/16)</p>
 */
class StudyRankingServiceTest extends AbstractIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int CAP = StudyRankingService.DAILY_CAP;

    @Autowired private StudyRankingService service;
    @Autowired private UserRepository userRepository;
    @Autowired private DailyUserStatRepository statRepository;

    private LocalDate today;
    private User top;       // 오늘 1등, 닉네임 가리기 켬
    private User second;    // 오늘 공동 2등
    private User tie;       // 오늘 공동 2등 (나)
    private User gone;      // 탈퇴 — 점수가 커도 안 나온다

    @BeforeEach
    void setUp() {
        statRepository.deleteAllInBatch();
        today = LocalDate.now(KST);
        top = user("qjwkr15", true);
        second = user("민수", false);
        tie = user("현동", false);
        gone = user("탈퇴한사람", false);
        gone.setDeletedAt(LocalDateTime.now());
        userRepository.save(gone);

        stat(top, today, 300);
        stat(second, today, 200);
        stat(tie, today, 200);
        stat(gone, today, 900);
        stat(second, today.minusDays(1), 150);   // 전체 합계에서는 second가 top을 넘는다 (350 > 300)
    }

    @Test
    @DisplayName("오늘 — 점수 순, 같은 점수는 같은 순위(1·2·2), 탈퇴 제외, 가린 닉네임은 앞 글자 + ***")
    void today() {
        StudyRankingResponse res = service.ranking(tie.getId(), "today", 50);

        // 목록이 통째로 이 셋 — 탈퇴한 사람도, 가린 사람의 진짜 닉네임(qjwkr15)도 없다
        assertEquals(List.of(
                new StudyRankingResponse.Entry(1, "qjw***", 300, false),
                new StudyRankingResponse.Entry(2, "민수", 200, false),
                new StudyRankingResponse.Entry(2, "현동", 200, true)), res.entries());
        assertEquals(new StudyRankingResponse.Entry(2, "현동", 200, true), res.me(), "내 순위 = 나보다 높은 사람 수 + 1");
        assertEquals("today", res.period());
        assertEquals(today, res.day());
    }

    @Test
    @DisplayName("전체 — 지금까지의 합계 순")
    void total() {
        StudyRankingResponse res = service.ranking(top.getId(), "all", 50);

        assertEquals(List.of(
                new StudyRankingResponse.Entry(1, "민수", 350, false),
                new StudyRankingResponse.Entry(2, "qjw***", 300, true),
                new StudyRankingResponse.Entry(3, "현동", 200, false)), res.entries());
        assertEquals(new StudyRankingResponse.Entry(2, "qjw***", 300, true), res.me(), "내 줄도 가린 이름으로 — 남들이 보는 그대로");
        assertNull(res.day());
    }

    @Test
    @DisplayName("하루 상한 (오늘) — 상한을 넘겨 답해도 점수는 상한까지. 상한에 닿은 사람끼리는 같은 순위이고 먼저 가입한 순 (더 많이 답했다고 위로 가지 않는다)")
    void dailyCap_today() {
        User exact = user("딱상한", false);           // 먼저 가입, 딱 상한만큼
        User heavy = user("큰덱되풀이", false);        // 나중 가입, 상한의 세 배 넘게
        User under = user("하나모자람", false);
        stat(exact, today, CAP);
        stat(heavy, today, CAP * 3 + 7);
        stat(under, today, CAP - 1);

        StudyRankingResponse res = service.ranking(heavy.getId(), "today", 50);

        assertEquals(List.of(
                new StudyRankingResponse.Entry(1, "딱상한", CAP, false),
                new StudyRankingResponse.Entry(1, "큰덱되풀이", CAP, true),
                new StudyRankingResponse.Entry(3, "하나모자람", CAP - 1, false)), res.entries().subList(0, 3));
        assertEquals(new StudyRankingResponse.Entry(1, "큰덱되풀이", CAP, true), res.me());
        assertEquals(CAP, res.dailyCap(), "상한 값은 응답에 실어 화면이 그대로 쓴다");

        // 목록 밖(상위 1명만 받음)이어도 내 순위는 상한을 적용한 점수끼리 센다
        assertEquals(new StudyRankingResponse.Entry(3, "하나모자람", CAP - 1, true), service.ranking(under.getId(), "today", 1).me());
    }

    @Test
    @DisplayName("하루 상한 (전체) — 날마다 상한까지만 더한다: 하루에 몰아서 한 사람이 여러 날 나눠 한 사람을 넘지 못한다")
    void dailyCap_total() {
        User binge = user("하루몰아", false);          // 오늘 상한의 5배 + 어제 10장 → 상한 + 10
        User steady = user("꾸준히", false);           // 이틀 동안 상한의 60%씩 → 상한의 1.2배
        stat(binge, today, CAP * 5);
        stat(binge, today.minusDays(1), 10);
        stat(steady, today, CAP * 6 / 10);
        stat(steady, today.minusDays(1), CAP * 6 / 10);

        StudyRankingResponse res = service.ranking(binge.getId(), "all", 50);

        // 합계를 한 번 자르는 게 아니라(그러면 둘 다 상한으로 같아진다) 날마다 자른 뒤 더한다
        assertEquals(List.of(
                new StudyRankingResponse.Entry(1, "꾸준히", CAP * 12 / 10, false),
                new StudyRankingResponse.Entry(2, "하루몰아", CAP + 10, true)), res.entries().subList(0, 2));
        assertEquals(new StudyRankingResponse.Entry(2, "하루몰아", CAP + 10, true), res.me());
        assertEquals(CAP, res.dailyCap());
        assertEquals(new StudyRankingResponse.Entry(2, "하루몰아", CAP + 10, true), service.ranking(binge.getId(), "all", 1).me(),
                "목록 밖이어도 내 순위는 상한을 적용한 합계끼리 센다");
    }

    @Test
    @DisplayName("점수가 없으면 내 순위는 null, 상위 몇 명만 달라고 하면 그만큼만 — 목록 밖이어도 내 줄은 따로 온다")
    void noPointsAndLimit() {
        User fresh = user("새사람", false);
        StudyRankingResponse res = service.ranking(fresh.getId(), "today", 1);
        assertNull(res.me());
        assertEquals(1, res.entries().size());

        StudyRankingResponse mine = service.ranking(tie.getId(), "today", 1);
        assertEquals(1, mine.entries().size());
        assertEquals(new StudyRankingResponse.Entry(2, "현동", 200, true), mine.me());
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
