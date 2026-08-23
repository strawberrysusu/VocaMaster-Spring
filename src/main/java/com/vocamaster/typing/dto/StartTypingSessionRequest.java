package com.vocamaster.typing.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class StartTypingSessionRequest {

    private String direction;       // 소문자 "front_to_back" / "back_to_front" (Direction.from이 value로 비교)
    private Integer total;          // 문제 수 (null이면 기본 10)
    private Boolean wrongOnly;      // 오답 카드만 (Quiz와 다르게 wrong 추적 다른 곳 — 일단 미사용 권장)
    private Boolean starredOnly;    // 즐겨찾기만
    private Long sourceSessionId;   // "이번 오답 다시" — 이 세션에서 틀린 카드만 (소유자·덱 검증). wrongOnly보다 우선
}
