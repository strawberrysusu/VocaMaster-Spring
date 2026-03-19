package com.vocamaster.card.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateCardRequest {

    @NotBlank
    private String front;

    @NotBlank
    private String back;
}
