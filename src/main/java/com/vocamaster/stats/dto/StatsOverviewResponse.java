package com.vocamaster.stats.dto;

import com.vocamaster.review.dto.BoxCountResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 통계 화면 한 장 = 응답 하나 (2026-08-23). 집계는 전부 GROUP BY — 덱마다 count 도는 N+1 없음.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsOverviewResponse {

    private List<DayActivity> days;          // 최근 28일, 활동 없는 날도 0으로 포함 (차트가 빈칸을 그리게)
    private int streak;                      // 현재 연속(표시 정책 A — 오늘 없으면 어제까지 유예)
    private int bestStreak;                  // 역대 최고 연속
    private long totalStudy;                 // 누적 학습 활동 수
    private long activeDays;                 // 학습한 날 수
    private List<BoxCountResponse> boxes;    // 라이트너 분포 (기존 쿼리 재사용)
    private List<DeckProgress> decks;        // 덱별 진행률

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayActivity {
        private LocalDate date;
        private int studyCount;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeckProgress {
        private Long deckId;
        private String title;
        private long cardCount;      // 전체
        private long started;        // 한 번이라도 답한 카드 (CardProgress 존재)
        private long mastered;       // 박스 5 이상 (14일+ 간격) — 기준은 StatsService.MASTERED_BOX
    }
}
