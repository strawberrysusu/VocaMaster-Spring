package com.vocamaster.deck.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateDeckRequest {
    private String title;
    private String description;
}
