package com.vocamaster.quiz.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QuizResultResponse {

    private Long id;
    private boolean correct;
    private String correctAnswer;
    private String selectedAnswer;
}
