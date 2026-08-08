package com.vocamaster.deck.dto;

import com.vocamaster.deck.DeckVisibility;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateVisibilityRequest {

    @NotNull(message = "visibility는 필수입니다")
    private DeckVisibility visibility;
}
