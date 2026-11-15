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
 * 학습 랭킹 (11/14 사용자 요청, ADR-059) — 답한 카드 1장 = 1점(하루 {@link #DAILY_CAP}점까지), 오늘 / 전체.
 *
 * <p>점수는 새로 쌓지 않는다: 서버가 채점을 기록하는 모드(낱말카드 복습·퀴즈·타이핑)가 이미 daily_user_stats.study_count에
 * 답변 수를 더하고 있다(ReviewService → StatsService.recordStudy). 오늘 = 그날 줄, 전체 = 날마다의 합계 — 둘 다 하루 상한에서 자른다.
 * 화면에서만 채점하는 듣기·시험지와 채점 없는 카드 학습은 서버에 기록이 없어 0점이다 (11/16 사용자 결정: 점수를 주지 않는다 — 그대로 둔다).</p>
 *
 * <p>모두 보이되(탈퇴 계정만 빠짐) 닉네임은 기본으로 가린다: 앞 몇 글자 + '***'. 설정에서 공개를 체크한 사람만 그대로 (11/15).
 * 가리기는 여기(서버)서 한다 — 화면에서 가리면 API 응답에 진짜 닉네임이 그대로 실려 간다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyRankingService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");   // 통계와 같은 'KST 자정' 기준 (StatsService)
    static final int MAX_LIMIT = 50;

    /**
     * 하루에 쌓이는 점수의 상한 (11/16 사용자 결정, ADR-059 보완 2).
     * 상한이 없으면 큰 덱을 되풀이하거나 일괄 제출(요청 하나에 답 1000개)을 거듭 보내 점수를 끝없이 올릴 수 있다.
     * 값의 근거 = 운영 실측(11/16): 하루에 가장 많이 답한 날이 568장, 1000장을 넘긴 날은 없다 → 성실한 학습은 깎이지 않는다.
     * 기록(study_count)은 그대로 두고 랭킹을 읽을 때만 자른다 (DailyUserStatRepository의 least).
     */
    static final int DAILY_CAP = 1000;

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
                ? statRepository.rankDay(day, DAILY_CAP, PageRequest.of(0, size))
                : statRepository.rankTotal(DAILY_CAP, PageRequest.of(0, size));

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

        return new StudyRankingResponse(today ? "today" : "all", today ? day : null, DAILY_CAP, entries, myEntry(meId, today, day));
    }

    /** 내 순위·점수 — 목록(상위 50) 밖이어도 알려 준다. 점수가 없으면 null. 목록과 같은 규칙(하루 상한)으로 센다 */
    private StudyRankingResponse.Entry myEntry(Long meId, boolean today, LocalDate day) {
        long points = today
                ? statRepository.findByUserIdAndStatDate(meId, day).map(s -> (long) Math.min(s.getStudyCount(), DAILY_CAP)).orElse(0L)
                : statRepository.cappedTotal(meId, DAILY_CAP);
        if (points <= 0) return null;
        long above = today
                ? statRepository.countDayAbove(day, points, DAILY_CAP)
                : statRepository.totalsAbove(points, DAILY_CAP).size();
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
