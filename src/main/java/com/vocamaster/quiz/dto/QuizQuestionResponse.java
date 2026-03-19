package com.vocamaster.quiz.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class QuizQuestionResponse {

    private Long cardId;
    private String question;
    private List<String> choices;
    private String direction;
}
