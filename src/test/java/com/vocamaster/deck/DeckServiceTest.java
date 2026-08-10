package com.vocamaster.deck;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeckServiceTest extends AbstractIntegrationTest {

    @Autowired private DeckService deckService;
    @Autowired private DeckRepository deckRepository;
    @Autowired private CardRepository cardRepository;
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

    // === 복사 (ADR-031) ===

    private User owner;

    private Deck ownersDeck(DeckVisibility visibility) {
        if (owner == null) {
            owner = userRepository.save(User.builder()
                    .email("owner@test.com").password("encoded").nickname("원본주인").build());
        }
        return deckRepository.save(Deck.builder()
                .title("토익 필수").description("공유용")
                .visibility(visibility).user(owner).build());
    }

    @Test
    @DisplayName("복사 성공 — 콘텐츠 복사·학습상태 리셋·owner 변경·출처 추적·copy_count +1")
    void copy_success() {
        Deck source = ownersDeck(DeckVisibility.PUBLIC);
        cardRepository.save(Card.builder().front("apple").back("사과")
                .exampleSentence("an apple a day").memo("암기팁").position(1)
                .starred(true).deck(source).build());
        cardRepository.save(Card.builder().front("banana").back("바나나")
                .position(2).deck(source).build());

        DeckResponse result = deckService.copy(source.getId(), user.getId());

        Deck copied = deckRepository.findById(result.getId()).orElseThrow();
        assertEquals(user.getId(), copied.getUser().getId());              // owner 변경
        assertEquals(DeckVisibility.PRIVATE, copied.getVisibility());      // 복사본은 무조건 PRIVATE
        assertEquals(source.getId(), copied.getOriginalDeckId());          // 출처 추적

        List<Card> copiedCards = cardRepository.findByDeckId(copied.getId());
        assertEquals(2, copiedCards.size());                               // 카드 개수 일치
        Card apple = copiedCards.stream()
                .filter(c -> c.getFront().equals("apple")).findFirst().orElseThrow();
        assertEquals("사과", apple.getBack());
        assertEquals("an apple a day", apple.getExampleSentence());
        assertEquals("암기팁", apple.getMemo());                            // memo = 공유 콘텐츠
        assertEquals(1, apple.getPosition());                              // position 유지
        assertFalse(apple.getStarred());                                   // 학습 상태는 리셋

        assertEquals(1, deckRepository.findById(source.getId()).orElseThrow().getCopyCount());
    }

    @Test
    @DisplayName("남의 PRIVATE 복사는 404 — 존재 숨김, 카운트 불변")
    void copy_othersPrivate_404() {
        Deck hidden = ownersDeck(DeckVisibility.PRIVATE);

        assertThrows(NotFoundException.class, () ->
                deckService.copy(hidden.getId(), user.getId()));

        assertEquals(0, deckRepository.findById(hidden.getId()).orElseThrow().getCopyCount());
    }

    @Test
    @DisplayName("남의 UNLISTED는 복사 가능 — 링크로 볼 수 있으면 복사도 일관")
    void copy_othersUnlisted_ok() {
        Deck linked = ownersDeck(DeckVisibility.UNLISTED);

        deckService.copy(linked.getId(), user.getId());

        assertEquals(1, deckRepository.findById(linked.getId()).orElseThrow().getCopyCount());
    }

    @Test
    @DisplayName("자기 덱은 PRIVATE이어도 복사 가능 — 단 copy_count 제외 (인기 조작 방지)")
    void copy_own_allowedButNotCounted() {
        // setUp의 deck = user 소유, 기본 PRIVATE
        DeckResponse result = deckService.copy(deck.getId(), user.getId());

        assertEquals(user.getId(),
                deckRepository.findById(result.getId()).orElseThrow().getUser().getId());
        assertEquals(0, deckRepository.findById(deck.getId()).orElseThrow().getCopyCount());
    }

    @Test
    @DisplayName("카드 0개 덱도 복사 허용")
    void copy_emptyDeck_ok() {
        Deck empty = ownersDeck(DeckVisibility.PUBLIC);

        DeckResponse result = deckService.copy(empty.getId(), user.getId());

        assertEquals(0, result.getCardCount());
        assertTrue(cardRepository.findByDeckId(result.getId()).isEmpty());
    }
}
