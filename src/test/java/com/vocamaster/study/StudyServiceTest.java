package com.vocamaster.study;

import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.study.dto.RecordStudyRequest;
import com.vocamaster.study.dto.StartStudyRequest;
import com.vocamaster.study.dto.StudySessionResponse;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StudyServiceTest {

    @Autowired private StudyService studyService;
    @Autowired private UserRepository userRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private CardRepository cardRepository;

    private User user;
    private Deck deck1;
    private Deck deck2;
    private Card cardInDeck1;
    private Card cardInDeck2;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("study@test.com")
                .password("encoded")
                .nickname("studier")
                .build());

        deck1 = deckRepository.save(Deck.builder().title("Deck 1").user(user).build());
        deck2 = deckRepository.save(Deck.builder().title("Deck 2").user(user).build());

        cardInDeck1 = cardRepository.save(Card.builder()
                .front("apple").back("사과").deck(deck1).build());
        cardInDeck2 = cardRepository.save(Card.builder()
                .front("banana").back("바나나").deck(deck2).build());
    }

    @Test
    @DisplayName("다른 deck의 카드를 학습 기록하면 거부됨")
    void recordAnswer_wrongDeckCard() {
        StartStudyRequest startReq = new StartStudyRequest();
        startReq.setDirection("front_to_back");

        StudySessionResponse session = studyService.startSession(
                deck1.getId(), user.getId(), startReq);

        // deck2의 카드로 deck1의 세션에 기록 시도
        RecordStudyRequest recordReq = new RecordStudyRequest();
        recordReq.setCardId(cardInDeck2.getId());
        recordReq.setKnown(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                studyService.recordAnswer(session.getSessionId(), user.getId(), recordReq));

        assertTrue(ex.getReason().contains("속하지 않는 카드"));
    }

    @Test
    @DisplayName("잘못된 direction 값 거부")
    void startSession_invalidDirection() {
        StartStudyRequest req = new StartStudyRequest();
        req.setDirection("invalid_direction");

        assertThrows(ResponseStatusException.class, () ->
                studyService.startSession(deck1.getId(), user.getId(), req));
    }

    @Test
    @DisplayName("정상 direction 값 허용")
    void startSession_validDirection() {
        StartStudyRequest req = new StartStudyRequest();
        req.setDirection("front_to_back");

        StudySessionResponse response = studyService.startSession(
                deck1.getId(), user.getId(), req);

        assertNotNull(response.getSessionId());
        assertEquals("front_to_back", response.getDirection());
    }
}
