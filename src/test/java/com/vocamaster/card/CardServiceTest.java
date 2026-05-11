package com.vocamaster.card;

import com.vocamaster.card.dto.CardResponse;
import com.vocamaster.card.dto.CreateCardRequest;
import com.vocamaster.card.dto.UpdateCardRequest;
import com.vocamaster.common.exception.ForbiddenException;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CardServiceTest {

    @Autowired private CardService cardService;
    @Autowired private CardRepository cardRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DeckRepository deckRepository;

    private User user;
    private Deck deck;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("card@test.com")
                .password("encoded")
                .nickname("carder")
                .build());

        deck = deckRepository.save(Deck.builder()
                .title("Test Deck")
                .user(user)
                .build());
    }

    @Test
    @DisplayName("카드 생성 성공")
    void create_success() {
        CreateCardRequest req = new CreateCardRequest();
        req.setFront("apple");
        req.setBack("사과");

        CardResponse card = cardService.create(deck.getId(), user.getId(), req);

        assertNotNull(card.getId());
        assertEquals("apple", card.getFront());
        assertEquals("사과", card.getBack());
        assertFalse(card.getStarred());
    }

    @Test
    @DisplayName("카드 수정 성공")
    void update_success() {
        CreateCardRequest createReq = new CreateCardRequest();
        createReq.setFront("apple");
        createReq.setBack("사과");
        CardResponse card = cardService.create(deck.getId(), user.getId(), createReq);

        UpdateCardRequest updateReq = new UpdateCardRequest();
        updateReq.setBack("빨간 사과");

        CardResponse updated = cardService.update(card.getId(), user.getId(), updateReq);

        assertEquals("apple", updated.getFront());
        assertEquals("빨간 사과", updated.getBack());
    }

    @Test
    @DisplayName("별표 토글")
    void toggleStar() {
        CreateCardRequest req = new CreateCardRequest();
        req.setFront("apple");
        req.setBack("사과");
        CardResponse card = cardService.create(deck.getId(), user.getId(), req);

        assertFalse(card.getStarred());

        CardResponse toggled = cardService.toggleStar(card.getId(), user.getId());
        assertTrue(toggled.getStarred());

        CardResponse toggledBack = cardService.toggleStar(card.getId(), user.getId());
        assertFalse(toggledBack.getStarred());
    }

    @Test
    @DisplayName("다른 사용자의 카드 접근 불가")
    void accessDenied() {
        CreateCardRequest req = new CreateCardRequest();
        req.setFront("apple");
        req.setBack("사과");
        CardResponse card = cardService.create(deck.getId(), user.getId(), req);

        User other = userRepository.save(User.builder()
                .email("other@test.com")
                .password("encoded")
                .nickname("other")
                .build());

        assertThrows(ForbiddenException.class, () ->
                cardService.findOne(card.getId(), other.getId()));
    }

    @Test
    @DisplayName("카드 삭제 성공")
    void delete_success() {
        CreateCardRequest req = new CreateCardRequest();
        req.setFront("apple");
        req.setBack("사과");
        CardResponse card = cardService.create(deck.getId(), user.getId(), req);

        cardService.remove(card.getId(), user.getId());

        assertFalse(cardRepository.findById(card.getId()).isPresent());
    }
    @Test
    @DisplayName("카드 목록 - keyword로 front/back 검색 + 전체 조회")
    void findAll_search() {
        // given: 카드 3개 생성
        createCard("apple", "사과");
        createCard("banana", "바나나");
        createCard("grape", "포도");

        // when 1 & then 1: 영어 front 검색
        Page<CardResponse> en = cardService.findAll(deck.getId(), user.getId(), 0, 20, "apple", null);
        assertEquals(1, en.getTotalElements());
        assertEquals("apple", en.getContent().get(0).getFront());

        // when 2 & then 2: 한국어 back 검색
        Page<CardResponse> ko = cardService.findAll(deck.getId(), user.getId(), 0, 20, "바나나", null);
        assertEquals(1, ko.getTotalElements());
        assertEquals("banana", ko.getContent().get(0).getFront());

        // when 3 & then 3: keyword=null이면 전체
        Page<CardResponse> all = cardService.findAll(deck.getId(), user.getId(), 0, 20, null, null);
        assertEquals(3, all.getTotalElements());
    }

    private void createCard(String front, String back) {
        CreateCardRequest req = new CreateCardRequest();
        req.setFront(front);
        req.setBack(back);
        cardService.create(deck.getId(), user.getId(), req);
    }

}
