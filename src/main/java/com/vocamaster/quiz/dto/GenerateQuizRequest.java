package com.vocamaster.quiz.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class GenerateQuizRequest {

    @NotBlank
    private String direction; // "front_to_back" or "back_to_front"

    private Boolean starredOnly = false;
    private Boolean wrongOnly = false;
}
