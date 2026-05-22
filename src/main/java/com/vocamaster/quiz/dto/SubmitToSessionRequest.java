package com.vocamaster.quiz.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class SubmitToSessionRequest {

    private Long questionId;        // 어느 문제에 답하는지
    private String selectedAnswer;  // 사용자가 고른 답
}
