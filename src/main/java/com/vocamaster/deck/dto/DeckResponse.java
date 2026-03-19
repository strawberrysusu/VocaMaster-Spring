package com.vocamaster.deck.dto;

import com.vocamaster.deck.Deck;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DeckResponse {

    private Long id;
    private String title;
    private String description;
    private long cardCount;
    private long starredCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DeckResponse from(Deck deck, long cardCount, long starredCount) {
        return DeckResponse.builder()
                .id(deck.getId())
                .title(deck.getTitle())
                .description(deck.getDescription() != null ? deck.getDescription() : "")
                .cardCount(cardCount)
                .starredCount(starredCount)
                .createdAt(deck.getCreatedAt())
                .updatedAt(deck.getUpdatedAt())
                .build();
    }

    // 목록 조회용 (starredCount 생략)
    public static DeckResponse listOf(Deck deck, long cardCount) {
        return DeckResponse.builder()
                .id(deck.getId())
                .title(deck.getTitle())
                .description(deck.getDescription() != null ? deck.getDescription() : "")
                .cardCount(cardCount)
                .createdAt(deck.getCreatedAt())
                .updatedAt(deck.getUpdatedAt())
                .build();
    }
}
