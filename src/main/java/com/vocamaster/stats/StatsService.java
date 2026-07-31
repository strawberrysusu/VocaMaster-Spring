package com.vocamaster.stats;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional
public class StatsService {

    // 정책이 "KST 자정 기준"이므로 서버 기본 시간대에 의존하지 않고 명시 (배포 서버가 UTC여도 동일 동작)
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyUserStatRepository dailyUserStatRepository;

    /**
     * 학습 활동 1회 = 출석 도장. 모든 학습 모드(Review/Quiz/Typing/Study)가 호출.
     * 호출한 쪽 트랜잭션에 합류하므로 답변 저장과 출석이 같이 성공하거나 같이 롤백된다.
     */
    public void recordStudy(Long userId) {
        LocalDate today = LocalDate.now(KST);

        // 오늘 줄이 이미 있으면 원자적 +1로 끝 (하루 중 두 번째부터 — 가장 흔한 경로, 쿼리 1번)
        int updated = dailyUserStatRepository.incrementStudyCount(userId, today);
        if (updated == 1) {
            return;
        }

        // 오늘 첫 학습 — 어제 줄을 보고 연속 여부 결정
        int streak = dailyUserStatRepository.findByUserIdAndStatDate(userId, today.minusDays(1))
                .map(yesterday -> yesterday.getStreak() + 1)    // 어제도 공부함 → 연속 +1
                .orElse(1);                                     // 끊김 → 1부터 다시

        // upsert — "첫 학습이 정확히 동시에 2건" 와도 한쪽은 INSERT, 한쪽은 +1로 흡수 (500 구멍 제거)
        dailyUserStatRepository.upsertTodayRow(userId, today, streak);
    }

    // 오늘 학습 답변 수 — 출석부 오늘 줄이 없으면 0 (아직 오늘 공부 전)
    @Transactional(readOnly = true)
    public int getTodayStudyCount(Long userId) {
        return dailyUserStatRepository.findByUserIdAndStatDate(userId, LocalDate.now(KST))
                .map(DailyUserStat::getStudyCount)
                .orElse(0);
    }

    // 표시용 streak (A 정책): 오늘 줄 있으면 오늘 값, 없으면 "어제까지의 연속"을 오늘 하루 유예로 보여줌.
    // 어제 줄도 없으면 끊김 확정 → 0
    @Transactional(readOnly = true)
    public int getDisplayStreak(Long userId) {
        LocalDate today = LocalDate.now(KST);
        return dailyUserStatRepository.findByUserIdAndStatDate(userId, today)
                .map(DailyUserStat::getStreak)
                .orElseGet(() -> dailyUserStatRepository.findByUserIdAndStatDate(userId, today.minusDays(1))
                        .map(DailyUserStat::getStreak)
                        .orElse(0));
    }
}
