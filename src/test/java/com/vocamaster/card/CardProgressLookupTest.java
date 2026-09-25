package com.vocamaster.card;

import com.vocamaster.card.dto.CardResponse;
import com.vocamaster.deck.DeckService;
import com.vocamaster.review.CardProgress;
import com.vocamaster.review.CardProgressRepository;
import com.vocamaster.review.LearningStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CardProgressLookupTest {

    private CardRepository cards;
    private CardProgressRepository progress;
    private CardService service;

    @BeforeEach
    void setUp() {
        cards = mock(CardRepository.class);
        progress = mock(CardProgressRepository.class);
        service = new CardService(cards, mock(DeckService.class), progress);
    }

    @Test
    void page_fetchesProgressOnceForOnlyVisibleCards() {
        Card first = Card.builder().id(21L).front("one").back("하나").build();
        Card second = Card.builder().id(22L).front("two").back("둘").build();
        when(cards.search(eq(9L), isNull(), isNull(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(1, 2), 10));
        when(progress.findByUserIdAndCardIdIn(7L, List.of(21L, 22L)))
                .thenReturn(List.of(CardProgress.builder().card(second).correctStreak(3)
                        .lastReviewedAt(LocalDateTime.now()).build()));

        Page<CardResponse> result = service.findAll(9L, 7L, 1, 2, null, null, null);

        assertEquals(10, result.getTotalElements());
        assertEquals(LearningStatus.UNKNOWN, result.getContent().get(0).getLearningStatus());
        assertEquals(LearningStatus.KNOWN, result.getContent().get(1).getLearningStatus());
        verify(progress).findByUserIdAndCardIdIn(7L, List.of(21L, 22L));
        verifyNoMoreInteractions(progress);
    }

    @Test
    void emptyPage_skipsProgressQuery() {
        when(cards.search(eq(9L), isNull(), isNull(), any(PageRequest.class)))
                .thenReturn(Page.empty());

        assertEquals(0, service.findAll(9L, 7L, 0, 20, null, null, null).getTotalElements());

        verifyNoInteractions(progress);
    }
}
