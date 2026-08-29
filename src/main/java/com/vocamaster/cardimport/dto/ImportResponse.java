package com.vocamaster.cardimport.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ImportResponse {

    private Long deckId;      // /decks/import-file(원자 등록) 응답에서만 채움 — 기존 경로는 null

    private int imported;
    private int skipped;
    private List<Map<String, Object>> failed;
    private int failedCount;
}
