package com.vocamaster.deck.dto;

import com.vocamaster.deck.Deck;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 공개 API 전용 응답 (ADR-030).
 * 내부용 DeckResponse와 분리 — 공개 표면은 노출 필드를 별도 계약으로 통제.
 * 작성자는 email이 아니라 닉네임만.
 */
@Getter
@Builder
public class PublicDeckResponse {

    private Long id;
    private String title;
    private String description;
    private String authorNickname;
    private long cardCount;
    private LocalDateTime createdAt;

    public static PublicDeckResponse from(Deck deck, long cardCount) {
        return PublicDeckResponse.builder()
                .id(deck.getId())
                .title(deck.getTitle())
                .description(deck.getDescription() != null ? deck.getDescription() : "")
                .authorNickname(deck.getUser().getNickname())
                .cardCount(cardCount)
                .createdAt(deck.getCreatedAt())
                .build();
    }
}
