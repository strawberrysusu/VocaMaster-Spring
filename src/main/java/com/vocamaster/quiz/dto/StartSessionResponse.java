package com.vocamaster.quiz.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class StartSessionResponse {

    private Long sessionId;
    private String direction;
    private int total;
    private List<QuestionDto> questions;

    @Getter
    @Builder
    public static class QuestionDto {
        private Long questionId;
        private int questionOrder;
        private String question;        // 화면 표시용
        private List<String> choices;   // 셔플된 4지선다
        // correctAnswer는 응답에 X — 서버만 알고 있음 (ADR-021 정신: 서버에서 판정)
    }
}
