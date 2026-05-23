package com.vocamaster.typing.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class SubmitTypedAnswerRequest {

    private Long questionId;
    private String typedAnswer;     // 사용자가 친 텍스트
}
