package com.vocamaster.quiz.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class SubmitToSessionRequest {

    @NotNull
    private Long questionId;        // 어느 문제에 답하는지
    @Size(max = 500)                // DB 컬럼 길이 초과 500 방지 (컨트롤러 @Valid가 400으로)
    private String selectedAnswer;  // 사용자가 고른 답
}
