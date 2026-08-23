package com.vocamaster.quiz.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class StartSessionRequest {

    private String direction;       // 소문자 "front_to_back" / "back_to_front" — Direction.from이 value로 비교 (2026-08-23 주석 정정)
    private Integer total;          // 문제 수 (null이면 기본 10, 1 이상)
    private Boolean wrongOnly;      // 누적 오답만 — 세션(quiz_questions) + 구형 단건(quiz_attempts) 통합 기준
    private Boolean starredOnly;    // 즐겨찾기만
    private Long sourceSessionId;   // "이번 오답 다시" — 이 세션에서 틀린 카드만 출제 (소유자·덱 검증). wrongOnly보다 우선
}
