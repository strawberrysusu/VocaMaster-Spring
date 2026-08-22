package com.vocamaster.study.event;

import java.time.LocalDate;

/**
 * "사용자가 이 덱으로 학습 활동을 1회 했다" — Phase 6 첫 도메인 이벤트.
 *
 * 발행: StatsService.recordStudy (모든 학습 모드의 단일 관문)
 * 구독: TodaySummaryCacheListener(요약 캐시 무효화, ADR-037) / DeckStudyRankingListener(인기 점수 study 항, ADR-038)
 *
 * 발행자는 구독자의 존재를 모른다 — 새 구독자가 생겨도 recordStudy는 안 바뀐다 (결합 해소).
 * deckId: 학습에 쓴 덱(복사본일 수 있음). 원본 귀속은 구독자(랭킹)의 일이지 발행자의 일이 아님.
 * record: 불변 데이터 묶음. 이벤트는 "일어난 사실"이라 바뀌면 안 된다.
 */
public record StudyRecordedEvent(Long userId, Long deckId, LocalDate date) {
}
