package com.vocamaster.wrongnote.dto;

import com.vocamaster.card.dto.CardResponse;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class WrongNoteResponse {

    private List<CardResponse> quiz;        // Quiz에서 틀린 카드들
    private List<CardResponse> typing;      // Typing에서 틀린 카드들
    private List<CardResponse> flashcard;   // Flashcard에서 "모름" 표시된 카드들
    private List<CardResponse> combined;    // 3 모드 합쳐 중복 제거 (LinkedHashSet 순서: quiz → typing → flashcard)
    private int total;                       // combined 크기
    private int days;                        // 적용된 시간 필터 (0=전체)
}
