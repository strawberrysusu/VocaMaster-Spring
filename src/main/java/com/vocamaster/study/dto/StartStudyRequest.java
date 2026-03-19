package com.vocamaster.study.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class StartStudyRequest {

    @NotBlank
    private String direction;

    private Boolean starredOnly = false;
}
