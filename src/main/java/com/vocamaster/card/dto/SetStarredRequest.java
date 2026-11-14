package com.vocamaster.card.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 별표 일괄 설정 — 값을 지정하는 멱등 요청 (토글 {@code PATCH /cards/{id}/star}와 달리 두 번 보내도 결과가 같다).
 * 온라인 시험지에서 틀린 카드를 한 번에 ⭐ 하고, '맞음으로'를 누르면 되돌린다.
 */
@Getter @Setter
public class SetStarredRequest {

    @NotEmpty(message = "카드를 하나 이상 골라야 합니다")
    @Size(max = 500, message = "한 번에 500장까지 설정할 수 있습니다")
    private List<@NotNull Long> cardIds;

    @NotNull(message = "starred 값이 필요합니다")
    private Boolean starred;
}
