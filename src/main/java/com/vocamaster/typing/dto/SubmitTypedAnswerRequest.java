package com.vocamaster.typing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class SubmitTypedAnswerRequest {

    @NotNull
    private Long questionId;
    @Size(max = 500)                // DB 컬럼 길이 초과 500 방지 (컨트롤러 @Valid가 400으로)
    private String typedAnswer;     // 사용자가 친 텍스트
}
