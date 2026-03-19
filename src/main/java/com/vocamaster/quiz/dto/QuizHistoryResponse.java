package com.vocamaster.quiz.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class QuizHistoryResponse {

    private List<AttemptDto> attempts;
    private long total;
    private long correct;
    private long wrong;
    private long accuracy;

    @Getter
    @Builder
    public static class AttemptDto {
        private Long id;
        private Long cardId;
        private String direction;
        private String selectedAnswer;
        private String correctAnswer;
        private boolean correct;
        private LocalDateTime createdAt;
    }
}
