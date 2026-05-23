package com.vocamaster.typing.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class StartTypingSessionRequest {

    private String direction;       // "FRONT_TO_BACK" / "BACK_TO_FRONT" (Direction.from은 소문자 value 비교)
    private Integer total;          // 문제 수 (null이면 기본 10)
    private Boolean wrongOnly;      // 오답 카드만 (Quiz와 다르게 wrong 추적 다른 곳 — 일단 미사용 권장)
    private Boolean starredOnly;    // 즐겨찾기만
}
