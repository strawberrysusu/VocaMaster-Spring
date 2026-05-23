package com.vocamaster.typing.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SubmitTypedAnswerResponse {

    private boolean correct;
    private String correctAnswer;   // 정답 공개 (학습 효과 — 푼 후엔 즉시 보여줘도 OK)
    private String typedAnswer;
    private boolean sessionEnded;
}
