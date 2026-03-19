package com.vocamaster.deck.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateDeckRequest {

    @NotBlank
    private String title;

    private String description;
}
