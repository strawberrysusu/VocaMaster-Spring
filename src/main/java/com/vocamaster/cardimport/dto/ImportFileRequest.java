package com.vocamaster.cardimport.dto;

import com.vocamaster.common.ValidationPatterns;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 파일 한 개 = 덱 생성 + 카드 등록 (단일 트랜잭션, Codex 검산 2026-08-29) */
@Getter @Setter
public class ImportFileRequest {

    @NotBlank
    @Pattern(regexp = ValidationPatterns.HAS_TEXT, message = "비워 둘 수 없습니다")   // 공백·안 보이는 글자만 있어도 빈칸 (수정·가져오기와 같은 규칙, 11/14)
    @Size(max = 255)          // decks.title varchar(255)
    private String title;

    @NotBlank
    private String text;

    private String separator; // 비우면 자동 감지 (기존 ImportRequest와 동일 규약)

    private Long folderId;    // 선택 — 만들면서 이 폴더로 (null = 미분류)
}
