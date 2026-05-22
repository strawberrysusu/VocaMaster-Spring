package com.vocamaster.quiz.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class SessionSummaryResponse {

    private Long sessionId;
    private String direction;
    private int total;          // 전체 문제 수
    private int answered;       // 푼 문제 수
    private int correct;        // 정답 수
    private int wrong;          // 오답 수
    private long accuracy;      // 정답률 (0~100, %)
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;   // NULL = 진행 중
    private List<QuestionResult> questions;

    @Getter
    @Builder
    public static class QuestionResult {
        private Long questionId;
        private int questionOrder;
        private String question;
        private List<String> choices;
        private String correctAnswer;    // 안 푼 문제는 NULL (정답 노출 방지)
        private String selectedAnswer;   // NULL = 미제출
        private Boolean correct;         // NULL = 미제출
    }
}
