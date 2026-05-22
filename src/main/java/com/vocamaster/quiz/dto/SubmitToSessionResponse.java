package com.vocamaster.quiz.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SubmitToSessionResponse {

    private boolean correct;
    private String correctAnswer;   // 정답 공개 (학습 효과)
    private String selectedAnswer;
    private boolean sessionEnded;   // 마지막 문제였는지
}
