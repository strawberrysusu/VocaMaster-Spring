package com.vocamaster.review.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ReviewAnswerRequest {

    // primitive boolean이면 빈 JSON {}이 false(오답 처리 → box 1 리셋)로 새는 사고 발생.
    // Boolean + @NotNull이면 누락 시 400 (RecordStudyRequest.known과 같은 관례)
    @NotNull
    private Boolean correct;
}
