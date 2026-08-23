package com.vocamaster.typing;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.typing.dto.*;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TypingServiceTest extends AbstractIntegrationTest {

    @Autowired private TypingService typingService;
    @Autowired private UserRepository userRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private TypingSessionRepository typingSessionRepository;
    @Autowired private TypingQuestionRepository typingQuestionRepository;

    @PersistenceContext
    private EntityManager em;

    private User user;
    private Deck deck;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("typing@test.com")
                .password("encoded")
                .nickname("typer")
                .build());
        deck = deckRepository.save(Deck.builder()
                .title("Typing Deck")
                .user(user)
                .build());
    }

    private void addCard(String front, String back) {
        cardRepository.save(Card.builder()
                .front(front).back(back).deck(deck).build());
    }

    private StartTypingSessionRequest startReq(Integer total) {
        StartTypingSessionRequest req = new StartTypingSessionRequest();
        req.setDirection("front_to_back");      // Direction.from은 소문자 value 비교
        req.setTotal(total);
        return req;
    }

    private SubmitTypedAnswerRequest submitReq(Long questionId, String typed) {
        SubmitTypedAnswerRequest req = new SubmitTypedAnswerRequest();
        req.setQuestionId(questionId);
        req.setTypedAnswer(typed);
        return req;
    }

    // ─────────────────────────────────────────────────────────────
    // 1. Eager 본질 — 시작 시 정확히 N개 row 생성
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("startSession - N개 문제 미리 생성 (Eager 본질, 선택지 없음)")
    void startSession_eagerCreation() {
        for (int i = 0; i < 5; i++) addCard("front" + i, "back" + i);
        em.flush();

        StartTypingSessionResponse res = typingService.startSession(deck.getId(), user.getId(), startReq(5));

        assertEquals(5, res.getTotal());
        assertEquals(5, res.getQuestions().size());

        em.flush();
        em.clear();
        List<TypingQuestion> questions = typingQuestionRepository
                .findBySessionIdOrderByQuestionOrderAsc(res.getSessionId());
        assertEquals(5, questions.size());
        // 선택지 없음 — choicesJson 필드 자체 없음 (엔티티 검증)
    }

    // ─────────────────────────────────────────────────────────────
    // 2. 정규화 — trim + ignoreCase 동시
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("submit - 공백 + 대소문자 무시 (trim + equalsIgnoreCase)")
    void submit_normalizeWhitespaceAndCase() {
        addCard("Word0", "Apple");
        for (int i = 1; i < 3; i++) addCard("W" + i, "A" + i);
        em.flush();

        StartTypingSessionResponse start = typingService.startSession(deck.getId(), user.getId(), startReq(3));
        // "Apple" 카드 찾아 첫 문제로 풀기
        TypingQuestion appleQ = typingQuestionRepository
                .findBySessionIdOrderByQuestionOrderAsc(start.getSessionId()).stream()
                .filter(q -> "Apple".equals(q.getCorrectAnswer()))
                .findFirst().orElseThrow();

        // "  apple  " 입력 — 양 끝 공백 + 소문자 — 정답 처리되어야
        SubmitTypedAnswerResponse res = typingService.submitTypedAnswer(
                start.getSessionId(), user.getId(), submitReq(appleQ.getId(), "  apple  "));

        assertTrue(res.isCorrect(), "trim + ignoreCase 정답 처리되어야");
    }

    // ─────────────────────────────────────────────────────────────
    // 3. 쉼표 복수 정답 — ADR-026 핵심
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("submit - 쉼표 복수 정답 (\"사과, 능금\" 중 하나만 맞아도 정답)")
    void submit_multipleAnswers() {
        addCard("apple", "사과, 능금");
        for (int i = 1; i < 3; i++) addCard("w" + i, "정답" + i);
        em.flush();

        StartTypingSessionResponse start = typingService.startSession(deck.getId(), user.getId(), startReq(3));
        TypingQuestion appleQ = typingQuestionRepository
                .findBySessionIdOrderByQuestionOrderAsc(start.getSessionId()).stream()
                .filter(q -> "사과, 능금".equals(q.getCorrectAnswer()))
                .findFirst().orElseThrow();

        // 두 번째 후보 "능금" 입력해도 정답
        SubmitTypedAnswerResponse res = typingService.submitTypedAnswer(
                start.getSessionId(), user.getId(), submitReq(appleQ.getId(), "능금"));

        assertTrue(res.isCorrect(), "쉼표 복수 정답 중 하나 맞히면 정답");
    }

    // ─────────────────────────────────────────────────────────────
    // 4. 빈 입력 오답
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("submit - 공백만/빈 입력은 오답")
    void submit_emptyInputRejected() {
        for (int i = 0; i < 3; i++) addCard("w" + i, "ans" + i);
        em.flush();

        StartTypingSessionResponse start = typingService.startSession(deck.getId(), user.getId(), startReq(3));
        Long firstQ = start.getQuestions().get(0).getQuestionId();

        SubmitTypedAnswerResponse res = typingService.submitTypedAnswer(
                start.getSessionId(), user.getId(), submitReq(firstQ, "   "));

        assertFalse(res.isCorrect(), "공백만 입력은 오답");
    }

    // ─────────────────────────────────────────────────────────────
    // 5. 마지막 문제 풀면 세션 자동 종료
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("submit - 마지막 문제 풀면 세션 자동 종료 (endedAt 채워짐)")
    void submit_lastQuestion_autoEndsSession() {
        for (int i = 0; i < 3; i++) addCard("X" + i, "Y" + i);
        em.flush();

        StartTypingSessionResponse start = typingService.startSession(deck.getId(), user.getId(), startReq(3));
        for (StartTypingSessionResponse.QuestionDto qDto : start.getQuestions()) {
            TypingQuestion q = typingQuestionRepository.findById(qDto.getQuestionId()).orElseThrow();
            typingService.submitTypedAnswer(start.getSessionId(), user.getId(),
                    submitReq(qDto.getQuestionId(), q.getCorrectAnswer()));
        }

        em.flush();
        em.clear();
        TypingSession session = typingSessionRepository.findById(start.getSessionId()).orElseThrow();
        assertNotNull(session.getEndedAt(), "마지막 문제 풀면 endedAt 자동 채워져야");
    }

    // ─────────────────────────────────────────────────────────────
    // 6. 재제출 차단
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("submit - 같은 문제 재제출 거부 (조작 방지)")
    void submit_rejectsReSubmit() {
        for (int i = 0; i < 3; i++) addCard("P" + i, "Q" + i);
        em.flush();

        StartTypingSessionResponse start = typingService.startSession(deck.getId(), user.getId(), startReq(3));
        Long firstQ = start.getQuestions().get(0).getQuestionId();

        typingService.submitTypedAnswer(start.getSessionId(), user.getId(), submitReq(firstQ, "anything"));

        assertThrows(BadRequestException.class, () ->
                typingService.submitTypedAnswer(start.getSessionId(), user.getId(), submitReq(firstQ, "again")),
                "재제출은 BadRequest");
    }

    // ─────────────────────────────────────────────────────────────
    // 7. 정답 노출 방지 — 안 푼 문제의 correctAnswer는 NULL
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("getSummary - 안 푼 문제 정답은 NULL (정답 노출 방지)")
    void getSummary_hidesAnswersOfUnanswered() {
        for (int i = 0; i < 3; i++) addCard("M" + i, "N" + i);
        em.flush();

        StartTypingSessionResponse start = typingService.startSession(deck.getId(), user.getId(), startReq(3));
        StartTypingSessionResponse.QuestionDto first = start.getQuestions().get(0);
        TypingQuestion firstQ = typingQuestionRepository.findById(first.getQuestionId()).orElseThrow();
        typingService.submitTypedAnswer(start.getSessionId(), user.getId(),
                submitReq(first.getQuestionId(), firstQ.getCorrectAnswer()));

        em.flush();
        em.clear();
        TypingSessionSummaryResponse summary = typingService.getSummary(start.getSessionId(), user.getId());

        for (TypingSessionSummaryResponse.QuestionResult r : summary.getQuestions()) {
            if (r.getQuestionId().equals(first.getQuestionId())) {
                assertNotNull(r.getCorrectAnswer(), "푼 문제는 정답 공개");
            } else {
                assertNull(r.getCorrectAnswer(), "안 푼 문제는 정답 NULL");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 8. wrongOnly — Typing 오답만 풀로 사용 (ADR-028 부수 결정)
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("startSession - wrongOnly=true면 Typing 오답 카드만 풀로 사용")
    void startSession_wrongOnly() {
        for (int i = 0; i < 5; i++) addCard("W" + i, "ans" + i);
        em.flush();

        // 1차 세션 — 첫 카드만 일부러 오답
        StartTypingSessionResponse first = typingService.startSession(deck.getId(), user.getId(), startReq(2));
        StartTypingSessionResponse.QuestionDto firstQ = first.getQuestions().get(0);
        typingService.submitTypedAnswer(first.getSessionId(), user.getId(),
                submitReq(firstQ.getQuestionId(), "intentionally-wrong"));

        em.flush();
        em.clear();

        // 2차 세션 — wrongOnly=true → 오답 카드 1장만 풀에 들어가야
        StartTypingSessionRequest wrongOnlyReq = new StartTypingSessionRequest();
        wrongOnlyReq.setDirection("front_to_back");
        wrongOnlyReq.setTotal(10);
        wrongOnlyReq.setWrongOnly(true);

        StartTypingSessionResponse retry = typingService.startSession(deck.getId(), user.getId(), wrongOnlyReq);

        // 풀이 1장이라 total=1
        assertEquals(1, retry.getTotal(), "오답 카드 1장만 풀");
        assertEquals(1, retry.getQuestions().size());
    }

    // ─────────────────────────────────────────────────────────────
    // "이번 오답 다시" — sourceSessionId (퀴즈와 같은 패턴, 2026-08-23)
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("이번 오답 다시 — 방금 세션에서 틀린 카드만, 1장이어도 가능")
    void retryFromSession_onlyWrongOfThatSession() {
        for (int i = 0; i < 4; i++) addCard("t" + i, "ans" + i);
        StartTypingSessionResponse first = typingService.startSession(deck.getId(), user.getId(), startReq(4));
        String wrongQuestion = null;
        for (int i = 0; i < first.getQuestions().size(); i++) {
            var q = first.getQuestions().get(i);
            String correct = typingQuestionRepository.findById(q.getQuestionId()).orElseThrow().getCorrectAnswer();
            SubmitTypedAnswerRequest req = new SubmitTypedAnswerRequest();
            req.setQuestionId(q.getQuestionId());
            req.setTypedAnswer(i == 0 ? "totally-wrong" : correct);
            if (i == 0) wrongQuestion = q.getQuestion();
            typingService.submitTypedAnswer(first.getSessionId(), user.getId(), req);
        }

        StartTypingSessionRequest retry = startReq(10);
        retry.setSourceSessionId(first.getSessionId());
        StartTypingSessionResponse second = typingService.startSession(deck.getId(), user.getId(), retry);

        assertEquals(1, second.getTotal());
        assertEquals(wrongQuestion, second.getQuestions().get(0).getQuestion());
    }

    @Test
    @DisplayName("남의 sourceSessionId는 403, 같은 사용자 다른 덱은 400, total 0은 400")
    void retryFromSession_guards() {
        for (int i = 0; i < 2; i++) addCard("g" + i, "h" + i);
        StartTypingSessionResponse mine = typingService.startSession(deck.getId(), user.getId(), startReq(2));
        var q = mine.getQuestions().get(0);
        SubmitTypedAnswerRequest wrong = new SubmitTypedAnswerRequest();
        wrong.setQuestionId(q.getQuestionId());
        wrong.setTypedAnswer("nope");
        typingService.submitTypedAnswer(mine.getSessionId(), user.getId(), wrong);

        User other = userRepository.save(User.builder()
                .email("other-typing@test.com").password("encoded").nickname("남").build());
        Deck othersDeck = deckRepository.save(Deck.builder().title("남의 덱").user(other).build());
        cardRepository.save(Card.builder().front("x").back("y").deck(othersDeck).build());
        StartTypingSessionRequest retry = startReq(5);
        retry.setSourceSessionId(mine.getSessionId());
        assertThrows(com.vocamaster.common.exception.ForbiddenException.class,
                () -> typingService.startSession(othersDeck.getId(), other.getId(), retry));

        Deck myOtherDeck = deckRepository.save(Deck.builder().title("내 다른 덱").user(user).build());
        cardRepository.save(Card.builder().front("m").back("n").deck(myOtherDeck).build());
        assertThrows(BadRequestException.class,
                () -> typingService.startSession(myOtherDeck.getId(), user.getId(), retry));

        assertThrows(BadRequestException.class,
                () -> typingService.startSession(deck.getId(), user.getId(), startReq(0)));
    }
}
