package com.vocamaster.deck;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.card.CardService;
import com.vocamaster.quiz.QuizService;
import com.vocamaster.quiz.dto.StartSessionRequest;
import com.vocamaster.quiz.dto.StartSessionResponse;
import com.vocamaster.quiz.dto.SubmitToSessionRequest;
import com.vocamaster.review.CardProgressRepository;
import com.vocamaster.review.ReviewService;
import com.vocamaster.typing.TypingService;
import com.vocamaster.typing.dto.StartTypingSessionRequest;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 삭제 정책 (ADR-040, V15): 학습 이력은 카드·덱과 생사를 같이한다 — CASCADE.
 * 전엔 12개 FK가 전부 RESTRICT라 한 번이라도 학습한 카드/덱 삭제가 500 (Codex 전수 감사 2026-08-23).
 * 같은 트랜잭션 안에서 flush로 FK 검사를 강제 — 예전 구조면 여기서 ConstraintViolation.
 */
class DeleteWithHistoryTest extends AbstractIntegrationTest {

    @Autowired private CardService cardService;
    @Autowired private DeckService deckService;
    @Autowired private QuizService quizService;
    @Autowired private TypingService typingService;
    @Autowired private ReviewService reviewService;
    @Autowired private CardRepository cardRepository;
    @Autowired private CardProgressRepository cardProgressRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private UserRepository userRepository;
    @PersistenceContext private EntityManager em;

    private User user;
    private Deck deck;
    private Card c1, c2, c3;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder().email("del@test.com").password("encoded").nickname("삭제자").build());
        deck = deckRepository.save(Deck.builder().title("삭제 덱").user(user).build());
        c1 = cardRepository.save(Card.builder().front("a").back("1").deck(deck).build());
        c2 = cardRepository.save(Card.builder().front("b").back("2").deck(deck).build());
        c3 = cardRepository.save(Card.builder().front("c").back("3").deck(deck).build());
        em.flush();

        // 이력 4종: 퀴즈 세션+답변(quiz_questions), 타이핑 세션(typing_questions), 복습(card_progress), 구형 퀴즈(quiz_attempts)는 세션으로 대체
        StartSessionRequest q = new StartSessionRequest();
        q.setDirection("front_to_back");
        q.setTotal(3);
        StartSessionResponse qs = quizService.startSession(deck.getId(), user.getId(), q);
        SubmitToSessionRequest ans = new SubmitToSessionRequest();
        ans.setQuestionId(qs.getQuestions().get(0).getQuestionId());
        ans.setSelectedAnswer("x");
        quizService.submitAnswerToSession(deck.getId(), qs.getSessionId(), user.getId(), ans);

        StartTypingSessionRequest t = new StartTypingSessionRequest();
        t.setDirection("front_to_back");
        t.setTotal(3);
        typingService.startSession(deck.getId(), user.getId(), t);

        reviewService.recordAnswer(user.getId(), c1.getId(), true);
        em.flush();
    }

    @Test
    @DisplayName("학습 이력(퀴즈·타이핑·복습)이 있는 카드 삭제 — 500 없이 이력까지 함께 사라진다")
    void deleteCard_withHistory() {
        assertTrue(cardProgressRepository.findByUserIdAndCardId(user.getId(), c1.getId()).isPresent(), "전제: 복습 이력 존재");

        assertDoesNotThrow(() -> {
            cardService.remove(c1.getId(), user.getId());
            em.flush();                                                   // FK 검사 강제
        });
        em.clear();
        assertFalse(cardRepository.findById(c1.getId()).isPresent());
        assertFalse(cardProgressRepository.findByUserIdAndCardId(user.getId(), c1.getId()).isPresent(), "복습 이력도 CASCADE");
    }

    @Test
    @DisplayName("세션(퀴즈·타이핑)이 달린 덱 삭제 — 카드·세션·문제 전부 함께")
    void deleteDeck_withSessions() {
        assertDoesNotThrow(() -> {
            deckService.remove(deck.getId(), user.getId());
            em.flush();
        });
        em.clear();
        assertFalse(deckRepository.findById(deck.getId()).isPresent());
        assertEquals(0, cardRepository.countByDeckId(deck.getId()));
    }

}
