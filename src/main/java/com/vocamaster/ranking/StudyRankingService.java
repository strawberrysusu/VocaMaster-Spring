package com.vocamaster.ranking;

import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.stats.DailyUserStatRepository;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 학습 랭킹 (11/14 사용자 요청, ADR-059) — 답한 카드 1장 = 1점, 오늘 / 전체.
 *
 * <p>점수는 새로 쌓지 않는다: 모든 학습 모드(복습·퀴즈·타이핑·듣기)가 이미 daily_user_stats.study_count에
 * 답변 수를 더하고 있다(StatsService.recordStudy). 오늘 = 그날 줄, 전체 = 합계. 채점 없는 카드 학습은 0점.</p>
 *
 * <p>모두 보이고(탈퇴 계정만 빠짐), 설정의 '닉네임 가리기'를 켠 사람은 앞 몇 글자 + '***'.
 * 가리기는 여기(서버)서 한다 — 화면에서 가리면 API 응답에 진짜 닉네임이 그대로 실려 간다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyRankingService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");   // 통계와 같은 'KST 자정' 기준 (StatsService)
    static final int MAX_LIMIT = 50;

    private final DailyUserStatRepository statRepository;
    private final UserRepository userRepository;

    public StudyRankingResponse ranking(Long meId, String period, int limit) {
        boolean today = switch (period) {
            case "today" -> true;
            case "all" -> false;
            default -> throw new BadRequestException("기간은 today 또는 all이어야 합니다");
        };
        int size = Math.max(1, Math.min(MAX_LIMIT, limit));
        LocalDate day = LocalDate.now(KST);

        List<Object[]> rows = today
                ? statRepository.rankDay(day, PageRequest.of(0, size))
                : statRepository.rankTotal(PageRequest.of(0, size));

        // 같은 점수면 같은 순위 (1, 2, 2, 4) — '나보다 높은 사람 수 + 1'과 같은 규칙
        List<StudyRankingResponse.Entry> entries = new ArrayList<>(rows.size());
        long prevPoints = -1;
        int prevRank = 0;
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            long points = ((Number) r[3]).longValue();
            int rank = points == prevPoints ? prevRank : i + 1;
            entries.add(new StudyRankingResponse.Entry(rank, displayName((String) r[1], (Boolean) r[2]), points, meId.equals(r[0])));
            prevPoints = points;
            prevRank = rank;
        }

        return new StudyRankingResponse(today ? "today" : "all", today ? day : null, entries, myEntry(meId, today, day));
    }

    /** 내 순위·점수 — 목록(상위 50) 밖이어도 알려 준다. 점수가 없으면 null */
    private StudyRankingResponse.Entry myEntry(Long meId, boolean today, LocalDate day) {
        long points = today
                ? statRepository.findByUserIdAndStatDate(meId, day).map(s -> (long) s.getStudyCount()).orElse(0L)
                : ((Number) statRepository.aggregate(meId).get(0)[0]).longValue();
        if (points <= 0) return null;
        long above = today ? statRepository.countDayAbove(day, points) : statRepository.totalsAbove(points).size();
        User me = userRepository.findById(meId).orElse(null);
        if (me == null) return null;
        return new StudyRankingResponse.Entry((int) above + 1, displayName(me.getNickname(), me.isRankingMasked()), points, true);
    }

    static String displayName(String nickname, Boolean masked) {
        return Boolean.TRUE.equals(masked) ? mask(nickname) : nickname;
    }

    /**
     * 닉네임 가리기 — 앞 글자 몇 개 + '***' (qjwkr15 → qjw***, 조현동 → 조***).
     * 남기는 글자 = 길이의 1/3(올림), 1~3글자. 별표는 늘 3개라 길이도 안 드러난다.
     * 글자는 코드 포인트로 센다 — 이모지·확장 한자를 반으로 가르면 '?'가 된다 (11/14 구글 닉네임과 같은 이유)
     */
    static String mask(String nickname) {
        if (nickname == null || nickname.isEmpty()) return "***";
        int length = nickname.codePointCount(0, nickname.length());
        int keep = Math.min(3, Math.max(1, (length + 2) / 3));
        return nickname.substring(0, nickname.offsetByCodePoints(0, keep)) + "***";
    }
}
