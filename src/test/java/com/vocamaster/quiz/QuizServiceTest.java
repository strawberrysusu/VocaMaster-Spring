package com.vocamaster.quiz;

import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.quiz.dto.GenerateQuizRequest;
import com.vocamaster.quiz.dto.QuizQuestionResponse;
import com.vocamaster.quiz.dto.QuizResultResponse;
import com.vocamaster.quiz.dto.SubmitQuizRequest;
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
class QuizServiceTest {

    @Autowired private QuizService quizService;
    @Autowired private UserRepository userRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private CardRepository cardRepository;

    private User user;
    private Deck deck;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("quiz@test.com")
                .password("encoded")
                .nickname("quizzer")
                .build());

        deck = deckRepository.save(Deck.builder()
                .title("Test Deck")
                .user(user)
                .build());
    }

    private void createCards(int count) {
        for (int i = 1; i <= count; i++) {
            cardRepository.save(Card.builder()
                    .front("word" + i)
                    .back("meaning" + i)
                    .deck(deck)
                    .build());
        }
    }

    @Test
    @DisplayName("퀴즈 생성 성공 - 5지선다 보기 확인")
    void generateQuiz_success() {
        createCards(6);

        GenerateQuizRequest req = new GenerateQuizRequest();
        req.setDirection("front_to_back");

        QuizQuestionResponse result = quizService.generateQuiz(deck.getId(), user.getId(), req);

        assertNotNull(result.getQuestion());
        assertNotNull(result.getCardId());
        assertEquals("front_to_back", result.getDirection());
        assertEquals(5, result.getChoices().size());
    }

    @Test
    @DisplayName("퀴즈 생성 실패 - 카드 5장 미만")
    void generateQuiz_notEnoughCards() {
        createCards(4);

        GenerateQuizRequest req = new GenerateQuizRequest();
        req.setDirection("front_to_back");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                quizService.generateQuiz(deck.getId(), user.getId(), req));

        assertTrue(ex.getReason().contains("최소 5개"));
    }

    @Test
    @DisplayName("퀴즈 정답 제출 - 서버 판정 정확")
    void submitAnswer_correct() {
        createCards(5);
        Card card = cardRepository.findByDeckId(deck.getId()).get(0);

        SubmitQuizRequest req = new SubmitQuizRequest();
        req.setCardId(card.getId());
        req.setSelectedAnswer(card.getBack()); // 정답
        req.setDirection("front_to_back");

        QuizResultResponse result = quizService.submitAnswer(deck.getId(), user.getId(), req);

        assertTrue(result.isCorrect());
        assertEquals(card.getBack(), result.getCorrectAnswer());
    }

    @Test
    @DisplayName("퀴즈 오답 제출 - 서버 판정 정확")
    void submitAnswer_wrong() {
        createCards(5);
        Card card = cardRepository.findByDeckId(deck.getId()).get(0);

        SubmitQuizRequest req = new SubmitQuizRequest();
        req.setCardId(card.getId());
        req.setSelectedAnswer("totally wrong");
        req.setDirection("front_to_back");

        QuizResultResponse result = quizService.submitAnswer(deck.getId(), user.getId(), req);

        assertFalse(result.isCorrect());
    }

    @Test
    @DisplayName("다른 사용자의 덱에 접근 불가")
    void generateQuiz_forbiddenDeck() {
        createCards(5);

        User other = userRepository.save(User.builder()
                .email("other@test.com")
                .password("encoded")
                .nickname("other")
                .build());

        GenerateQuizRequest req = new GenerateQuizRequest();
        req.setDirection("front_to_back");

        assertThrows(ResponseStatusException.class, () ->
                quizService.generateQuiz(deck.getId(), other.getId(), req));
    }

    @Test
    @DisplayName("잘못된 direction 값이면 퀴즈 생성 거부")
    void generateQuiz_invalidDirection() {
        createCards(6);

        GenerateQuizRequest req = new GenerateQuizRequest();
        req.setDirection("wrong_value");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                quizService.generateQuiz(deck.getId(), user.getId(), req));

        assertTrue(ex.getReason().contains("front_to_back"));
    }

    @Test
    @DisplayName("잘못된 direction 값이면 퀴즈 제출 거부")
    void submitAnswer_invalidDirection() {
        createCards(5);
        Card card = cardRepository.findByDeckId(deck.getId()).get(0);

        SubmitQuizRequest req = new SubmitQuizRequest();
        req.setCardId(card.getId());
        req.setSelectedAnswer("anything");
        req.setDirection("garbage");

        assertThrows(ResponseStatusException.class, () ->
                quizService.submitAnswer(deck.getId(), user.getId(), req));
    }
}
