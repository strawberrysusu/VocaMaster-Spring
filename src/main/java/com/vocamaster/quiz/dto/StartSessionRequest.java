package com.vocamaster.quiz.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class StartSessionRequest {

    private String direction;       // "FRONT_TO_BACK" / "BACK_TO_FRONT"
    private Integer total;          // 문제 수 (null이면 기본 10)
    private Boolean wrongOnly;      // 오답 카드만
    private Boolean starredOnly;    // 즐겨찾기만
}
