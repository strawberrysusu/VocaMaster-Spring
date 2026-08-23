package com.vocamaster.wrongnote;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.quiz.QuizAttempt;
import com.vocamaster.quiz.QuizAttemptRepository;
import com.vocamaster.study.StudyRecord;
import com.vocamaster.study.StudyRecordRepository;
import com.vocamaster.study.StudySession;
import com.vocamaster.study.StudySessionRepository;
import com.vocamaster.typing.TypingQuestion;
import com.vocamaster.typing.TypingQuestionRepository;
import com.vocamaster.typing.TypingSession;
import com.vocamaster.typing.TypingSessionRepository;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import com.vocamaster.wrongnote.dto.WrongNoteResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class WrongNoteServiceTest extends AbstractIntegrationTest {

    @Autowired private WrongNoteService wrongNoteService;
    @Autowired private UserRepository userRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private QuizAttemptRepository quizAttemptRepository;
    @Autowired private com.vocamaster.quiz.QuizService quizService;
    @Autowired private com.vocamaster.quiz.QuizQuestionRepository quizQuestionRepository;
    @Autowired private TypingSessionRepository typingSessionRepository;
    @Autowired private TypingQuestionRepository typingQuestionRepository;
    @Autowired private StudySessionRepository studySessionRepository;
    @Autowired private StudyRecordRepository studyRecordRepository;

    @PersistenceContext
    private EntityManager em;

    private User user;
    private Deck deck;
    private Card cardA, cardB, cardC, cardD, cardE;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("wrong@test.com")
                .password("encoded")
                .nickname("wronger")
                .build());
        deck = deckRepository.save(Deck.builder()
                .title("Wrong Note Deck")
                .user(user)
                .build());

        cardA = cardRepository.save(Card.builder().front("A").back("ans-A").deck(deck).build());
        cardB = cardRepository.save(Card.builder().front("B").back("ans-B").deck(deck).build());
        cardC = cardRepository.save(Card.builder().front("C").back("ans-C").deck(deck).build());
        cardD = cardRepository.save(Card.builder().front("D").back("ans-D").deck(deck).build());
        cardE = cardRepository.save(Card.builder().front("E").back("ans-E").deck(deck).build());
    }

    // ─────────────────────────────────────────────────────────────
    // 통합 + 중복 제거 — ADR-028 핵심
    //
    // Quiz 오답 = [A, B]
    // Typing 오답 = [B, C]   ← B 중복
    // Flashcard "모름" = [C, D] ← C 중복
    // 기대: quiz=[A,B], typing=[B,C], flashcard=[C,D],
    //       combined=[A,B,C,D]  (총 4개, E는 등장 X)
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("getWrongNotes - 3 모드 통합 + 같은 카드 중복 제거")
    void getWrongNotes_aggregatesAndDedupes() {
        // Quiz: A, B 오답
        quizAttemptRepository.save(QuizAttempt.builder()
                .deckId(deck.getId()).user(user).card(cardA)
                .direction("front_to_back").selectedAnswer("x").correctAnswer("ans-A")
                .isCorrect(false).build());
        quizAttemptRepository.save(QuizAttempt.builder()
                .deckId(deck.getId()).user(user).card(cardB)
                .direction("front_to_back").selectedAnswer("y").correctAnswer("ans-B")
                .isCorrect(false).build());

        // Typing: B, C 오답 (B 중복)
        TypingSession tSession = typingSessionRepository.save(TypingSession.builder()
                .user(user).deck(deck).direction("FRONT_TO_BACK").total(2).build());
        typingQuestionRepository.save(TypingQuestion.builder()
                .session(tSession).card(cardB).questionOrder(0)
                .questionText("B").correctAnswer("ans-B")
                .typedAnswer("wrong").isCorrect(false).answeredAt(LocalDateTime.now()).build());
        typingQuestionRepository.save(TypingQuestion.builder()
                .session(tSession).card(cardC).questionOrder(1)
                .questionText("C").correctAnswer("ans-C")
                .typedAnswer("wrong").isCorrect(false).answeredAt(LocalDateTime.now()).build());

        // Flashcard: C, D "모름" (C 중복)
        StudySession sSession = studySessionRepository.save(StudySession.builder()
                .user(user).deck(deck).direction("front_to_back").starredOnly(false).build());
        studyRecordRepository.save(StudyRecord.builder()
                .session(sSession).card(cardC).known(false).build());
        studyRecordRepository.save(StudyRecord.builder()
                .session(sSession).card(cardD).known(false).build());

        em.flush();
        em.clear();

        WrongNoteResponse res = wrongNoteService.getWrongNotes(deck.getId(), user.getId(), 30);

        // 모드별 검증
        assertEquals(Set.of(cardA.getId(), cardB.getId()),
                res.getQuiz().stream().map(c -> c.getId()).collect(Collectors.toSet()),
                "Quiz 오답 = [A, B]");
        assertEquals(Set.of(cardB.getId(), cardC.getId()),
                res.getTyping().stream().map(c -> c.getId()).collect(Collectors.toSet()),
                "Typing 오답 = [B, C]");
        assertEquals(Set.of(cardC.getId(), cardD.getId()),
                res.getFlashcard().stream().map(c -> c.getId()).collect(Collectors.toSet()),
                "Flashcard 모름 = [C, D]");

        // 통합 — 중복 제거 검증 (B, C 중복이지만 각 1번만)
        assertEquals(4, res.getCombined().size(), "combined = [A, B, C, D] 4개 (중복 제거)");
        assertEquals(4, res.getTotal());
        Set<Long> combinedIds = res.getCombined().stream().map(c -> c.getId()).collect(Collectors.toSet());
        assertEquals(Set.of(cardA.getId(), cardB.getId(), cardC.getId(), cardD.getId()), combinedIds);
        assertFalse(combinedIds.contains(cardE.getId()), "E는 오답 없음 → combined에 X");
    }

    @Test
    @DisplayName("세션 퀴즈(quiz_questions)의 오답도 통합 오답노트에 잡힌다 — 구형 quiz_attempts만 보던 구멍 (Codex 검산 2026-08-23)")
    void sessionQuizWrong_appearsInWrongNote() {
        // 세션 시작 → 첫 문제를 일부러 틀림 (quiz_attempts에는 아무것도 안 쌓임)
        com.vocamaster.quiz.dto.StartSessionRequest start = new com.vocamaster.quiz.dto.StartSessionRequest();
        start.setDirection("front_to_back");
        start.setTotal(5);
        var session = quizService.startSession(deck.getId(), user.getId(), start);
        var q = session.getQuestions().get(0);
        String correct = quizQuestionRepository.findById(q.getQuestionId()).orElseThrow().getCorrectAnswer();
        String wrongPick = q.getChoices().stream().filter(c -> !c.equals(correct)).findFirst().orElseThrow();
        com.vocamaster.quiz.dto.SubmitToSessionRequest submit = new com.vocamaster.quiz.dto.SubmitToSessionRequest();
        submit.setQuestionId(q.getQuestionId());
        submit.setSelectedAnswer(wrongPick);
        quizService.submitAnswerToSession(session.getSessionId(), user.getId(), submit);
        em.flush();

        var res = wrongNoteService.getWrongNotes(deck.getId(), user.getId(), 0);
        Set<Long> quizIds = res.getQuiz().stream().map(c -> c.getId()).collect(Collectors.toSet());
        Long wrongCardId = quizQuestionRepository.findById(q.getQuestionId()).orElseThrow().getCard().getId();
        assertTrue(quizIds.contains(wrongCardId), "세션에서 틀린 카드가 오답노트 quiz 항목에 있어야");
        assertTrue(res.getCombined().stream().anyMatch(c -> c.getId().equals(wrongCardId)), "combined에도");
    }
}
