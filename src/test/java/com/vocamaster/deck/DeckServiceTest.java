package com.vocamaster.deck;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.common.exception.ForbiddenException;
import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.deck.dto.CreateDeckRequest;
import com.vocamaster.deck.dto.DeckResponse;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class DeckServiceTest extends AbstractIntegrationTest {

    @Autowired private DeckService deckService;
    @Autowired private DeckRepository deckRepository;
    @Autowired private UserRepository userRepository;

    private User user;
    private Deck deck;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("deck@test.com")
                .password("encoded")
                .nickname("decker")
                .build());

        deck = deckRepository.save(Deck.builder()
                .title("Visibility Deck")
                .user(user)
                .build());
    }

    @Test
    @DisplayName("새 덱은 기본 PRIVATE — 응답 매핑까지 확인")
    void create_defaultPrivate() {
        CreateDeckRequest req = new CreateDeckRequest();
        req.setTitle("새 덱");

        DeckResponse created = deckService.create(user.getId(), req);

        assertEquals(DeckVisibility.PRIVATE, created.getVisibility());
        Deck saved = deckRepository.findById(created.getId()).orElseThrow();
        assertEquals(DeckVisibility.PRIVATE, saved.getVisibility());
    }

    @Test
    @DisplayName("공개 범위 변경 성공 — 응답과 DB 모두 PUBLIC")
    void updateVisibility_success() {
        DeckResponse updated = deckService.updateVisibility(
                deck.getId(), user.getId(), DeckVisibility.PUBLIC);

        assertEquals(DeckVisibility.PUBLIC, updated.getVisibility());
        Deck saved = deckRepository.findById(deck.getId()).orElseThrow();
        assertEquals(DeckVisibility.PUBLIC, saved.getVisibility());
    }

    @Test
    @DisplayName("남의 덱 공개 범위 변경 시 403 — 기존 값 유지")
    void updateVisibility_forbidden() {
        User other = userRepository.save(User.builder()
                .email("other@test.com")
                .password("encoded")
                .nickname("other")
                .build());

        assertThrows(ForbiddenException.class, () ->
                deckService.updateVisibility(deck.getId(), other.getId(), DeckVisibility.PUBLIC));

        Deck saved = deckRepository.findById(deck.getId()).orElseThrow();
        assertEquals(DeckVisibility.PRIVATE, saved.getVisibility());
    }

    @Test
    @DisplayName("없는 덱이면 404")
    void updateVisibility_notFound() {
        assertThrows(NotFoundException.class, () ->
                deckService.updateVisibility(999_999L, user.getId(), DeckVisibility.PUBLIC));
    }
}
