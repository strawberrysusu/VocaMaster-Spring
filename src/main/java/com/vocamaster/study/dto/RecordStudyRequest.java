package com.vocamaster.study.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RecordStudyRequest {

    @NotNull
    private Long cardId;

    @NotNull
    private Boolean known;
}
