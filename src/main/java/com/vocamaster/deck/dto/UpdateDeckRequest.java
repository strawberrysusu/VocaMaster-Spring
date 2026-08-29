package com.vocamaster.deck.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateDeckRequest {
    @Size(max = 255)          // DB 컬럼(varchar 255)과 일치 — 초과 입력은 500이 아니라 400 (Codex 검산 8/29)
    private String title;
    @Size(max = 255)
    private String description;
}
