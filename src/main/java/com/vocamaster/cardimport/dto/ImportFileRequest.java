package com.vocamaster.cardimport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 파일 한 개 = 덱 생성 + 카드 등록 (단일 트랜잭션, Codex 검산 2026-08-29) */
@Getter @Setter
public class ImportFileRequest {

    @NotBlank
    @Size(max = 255)          // decks.title varchar(255)
    private String title;

    @NotBlank
    private String text;

    private String separator; // 비우면 자동 감지 (기존 ImportRequest와 동일 규약)
}
