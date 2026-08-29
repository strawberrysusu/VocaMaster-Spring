package com.vocamaster.deck.dto;

import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckVisibility;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DeckResponse {

    private Long id;
    private String title;
    private String description;
    private DeckVisibility visibility;
    private long cardCount;
    private long starredCount;
    private Long folderId;   // null = 미분류
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DeckResponse from(Deck deck, long cardCount, long starredCount) {
        return DeckResponse.builder()
                .id(deck.getId())
                .title(deck.getTitle())
                .description(deck.getDescription() != null ? deck.getDescription() : "")
                .visibility(deck.getVisibility())
                .cardCount(cardCount)
                .starredCount(starredCount)
                .folderId(deck.getFolderId())
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
                .visibility(deck.getVisibility())
                .cardCount(cardCount)
                .folderId(deck.getFolderId())
                .createdAt(deck.getCreatedAt())
                .updatedAt(deck.getUpdatedAt())
                .build();
    }
}
