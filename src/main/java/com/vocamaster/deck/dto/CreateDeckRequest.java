package com.vocamaster.deck.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateDeckRequest {

    @NotBlank
    @Size(max = 255)          // decks.title varchar(255)
    private String title;

    @Size(max = 255)
    private String description;
}
