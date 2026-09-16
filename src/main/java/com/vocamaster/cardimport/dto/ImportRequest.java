package com.vocamaster.cardimport.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ImportRequest {

    @NotBlank
    private String text;

    private String separator;   // null 또는 빈 문자열이면 ImportService가 자동 감지 (ADR-022). 탭 하나는 명시 구분자 (9/5 감사 F2)
}
