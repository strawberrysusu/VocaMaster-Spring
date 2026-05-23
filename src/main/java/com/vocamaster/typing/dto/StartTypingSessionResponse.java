package com.vocamaster.typing.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class StartTypingSessionResponse {

    private Long sessionId;
    private String direction;
    private int total;
    private List<QuestionDto> questions;

    @Getter
    @Builder
    public static class QuestionDto {
        private Long questionId;
        private int questionOrder;
        private String question;        // 화면 표시용 (정답은 서버만 알고 있음)
    }
}
