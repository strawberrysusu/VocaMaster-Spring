package com.vocamaster.quiz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class SubmitQuizRequest {

    @NotNull
    private Long cardId;

    @NotBlank
    private String selectedAnswer;

    @NotBlank
    private String direction;
}
