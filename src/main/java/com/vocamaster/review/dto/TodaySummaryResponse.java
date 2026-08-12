package com.vocamaster.review.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
// Redis JSON 역직렬화용 — Jackson이 빈 객체를 만들고 필드에 직접 채움 (RedisConfig의 FIELD 접근 설정과 한 쌍).
// 이게 없으면 "저장은 되는데 되읽기가 실패"하는 반쪽 캐시가 됨 (List.of 함정의 사촌).
// @AllArgsConstructor는 @Builder의 재료 — 생성자 어노테이션이 하나라도 붙으면 롬복이 자동 생성을 멈추므로 명시
@NoArgsConstructor(access = AccessLevel.PRIVATE, force = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TodaySummaryResponse {

    private long dueCount;              // 지금 복습 시간이 지난 카드 수 (남은 숙제)
    private long reviewedTodayCount;    // 오늘 복습 답변한 서로 다른 카드 수 (장수 — due 완료 수가 아님)
    private int studyCount;             // 오늘 모든 학습 모드의 답변 수 (횟수)
    private int streak;                 // 연속 학습 일수 (A 정책: 오늘 전엔 어제 값 유지)
}
