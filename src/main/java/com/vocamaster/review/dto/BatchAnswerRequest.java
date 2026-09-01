package com.vocamaster.review.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 학습 세션 일괄 제출 (2026-08-31).
 *
 * <p>세션 도중의 알아요/몰라요는 프론트의 임시 상태다. '완료'를 누를 때 최종 답안만 한 번 올라온다.</p>
 */
@Getter @Setter
public class BatchAnswerRequest {

    /** 한 요청의 답변 수 상한. 덱 크기가 아니라 '한 세션에서 사람이 답할 수 있는 양'의 안전선 */
    public static final int MAX_ANSWERS = 1000;

    /** 클라이언트가 세션 시작 때 1회 생성하는 UUID. 같은 값으로 두 번 오면 진행도는 다시 움직이지 않는다 */
    @NotNull
    @Size(min = 1, max = 36)
    private String submissionId;

    @NotEmpty(message = "답변이 비어 있습니다")
    @Size(max = MAX_ANSWERS, message = "한 번에 보낼 수 있는 답변은 " + MAX_ANSWERS + "개까지입니다")
    @Valid
    private List<Item> answers;

    @Getter @Setter
    public static class Item {

        @NotNull
        private Long cardId;

        // primitive boolean이면 빈 JSON {}이 false(오답 → box 1 리셋)로 새는 사고가 난다.
        // Boolean + @NotNull이면 누락 시 400 (ReviewAnswerRequest.correct와 같은 관례)
        @NotNull
        private Boolean correct;
    }
}
