package com.vocamaster.study.event;

import java.time.LocalDate;

/**
 * "사용자가 학습 활동을 1회 했다" — Phase 6 첫 도메인 이벤트.
 *
 * 발행: StatsService.recordStudy (모든 학습 모드의 단일 관문)
 * 구독: 관심 있는 쪽이 각자 — 요약 캐시 무효화(지금), 인기 점수 study 항·배지 등(이후)
 *
 * 발행자는 구독자의 존재를 모른다 — 새 구독자가 생겨도 recordStudy는 안 바뀐다 (결합 해소, ADR-037).
 * record: 불변 데이터 묶음. 이벤트는 "일어난 사실"이라 바뀌면 안 된다.
 */
public record StudyRecordedEvent(Long userId, LocalDate date) {
}
