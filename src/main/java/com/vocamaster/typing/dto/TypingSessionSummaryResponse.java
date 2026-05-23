package com.vocamaster.typing.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class TypingSessionSummaryResponse {

    private Long sessionId;
    private String direction;
    private int total;
    private int answered;
    private int correct;
    private int wrong;
    private long accuracy;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private List<QuestionResult> questions;

    @Getter
    @Builder
    public static class QuestionResult {
        private Long questionId;
        private int questionOrder;
        private String question;
        private String correctAnswer;   // 안 푼 문제는 NULL (정답 노출 방지)
        private String typedAnswer;     // NULL = 미제출
        private Boolean correct;        // NULL = 미제출
    }
}
