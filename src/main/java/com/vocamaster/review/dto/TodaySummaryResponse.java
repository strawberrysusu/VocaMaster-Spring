package com.vocamaster.review.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TodaySummaryResponse {

    private long dueCount;              // 지금 복습 시간이 지난 카드 수 (남은 숙제)
    private long reviewedTodayCount;    // 오늘 복습 답변한 서로 다른 카드 수 (장수 — due 완료 수가 아님)
    private int studyCount;             // 오늘 모든 학습 모드의 답변 수 (횟수)
    private int streak;                 // 연속 학습 일수 (A 정책: 오늘 전엔 어제 값 유지)
}
