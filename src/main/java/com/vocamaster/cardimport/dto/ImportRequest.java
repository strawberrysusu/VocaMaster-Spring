package com.vocamaster.cardimport.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ImportRequest {

    @NotBlank
    private String text;

    private String separator = " - ";
}
