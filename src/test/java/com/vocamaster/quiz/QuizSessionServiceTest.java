package com.vocamaster.quiz;

import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.common.exception.ForbiddenException;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.quiz.dto.*;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import com.vocamaster.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuizSessionServiceTest extends AbstractIntegrationTest {

    @Autowired private QuizService quizService;
    @Autowired private UserRepository userRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private QuizSessionRepository quizSessionRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;

    @PersistenceContext
    private EntityManager em;

    private User user;
    private Deck deck;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("quiz@test.com")
                .password("encoded")
                .nickname("quizer")
                .build());
        deck = deckRepository.save(Deck.builder()
                .title("Quiz Deck")
                .user(user)
                .build());
    }

    private void addCard(String front, String back) {
        cardRepository.save(Card.builder()
                .front(front).back(back).deck(deck).build());
    }

    private StartSessionRequest startReq(Integer total) {
        StartSessionRequest req = new StartSessionRequest();
        req.setDirection("front_to_back");      // Direction.from은 소문자 value 비교
        req.setTotal(total);
        return req;
    }

    // ─────────────────────────────────────────────────────────────
    // 1. Eager 본질 — 시작 시 정확히 N개 row 생성
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("startSession - N개 문제 미리 생성 (Eager 본질)")
    void startSession_eagerCreation() {
        for (int i = 0; i < 12; i++) addCard("front" + i, "back" + i);
        em.flush();

        StartSessionResponse res = quizService.startSession(deck.getId(), user.getId(), startReq(10));

        assertEquals(10, res.getTotal());
        assertEquals(10, res.getQuestions().size());

        // 화이트보드 → DB 옮기고 → 화이트보드 지움 → 진짜 DB 검증
        em.flush();
        em.clear();
        List<QuizQuestion> questions = quizQuestionRepository
                .findBySessionIdOrderByQuestionOrderAsc(res.getSessionId());
        assertEquals(10, questions.size(), "DB에 실제로 10개 row 저장되어야");
    }

    // ─────────────────────────────────────────────────────────────
    // 2. fallback — 카드 5개 미만이면 풀 크기로 축소
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("startSession - 카드 5개 미만이면 fallback (3지선다)")
    void startSession_fallback_lessThan5Cards() {
        addCard("a", "A");
        addCard("b", "B");
        addCard("c", "C");
        em.flush();

        StartSessionResponse res = quizService.startSession(deck.getId(), user.getId(), startReq(10));

        assertEquals(3, res.getTotal(), "카드 3장이면 3문제만");
        for (StartSessionResponse.QuestionDto q : res.getQuestions()) {
            assertEquals(3, q.getChoices().size(), "3지선다 fallback");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. 선택지 중복 제거 — 같은 답을 가진 카드들 있어도 OK
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("startSession - 같은 답 카드 있어도 선택지 중복 없음")
    void startSession_choicesNoDuplicate() {
        addCard("apple", "과일");      // 같은 답
        addCard("orchard", "과일");    // 같은 답
        addCard("banana", "바나나");
        addCard("cherry", "체리");
        addCard("grape", "포도");
        em.flush();

        StartSessionResponse res = quizService.startSession(deck.getId(), user.getId(), startReq(5));

        for (StartSessionResponse.QuestionDto q : res.getQuestions()) {
            long distinct = q.getChoices().stream().distinct().count();
            assertEquals(q.getChoices().size(), distinct, "선택지에 같은 값 두 번 X");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. 정답 비교 정규화 — 공백/대소문자 무시
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("submit - 정답 비교 정규화 (공백/대소문자 무시)")
    void submitAnswer_normalizeWhitespace() {
        for (int i = 0; i < 5; i++) addCard("Word" + i, "Answer" + i);
        em.flush();

        StartSessionResponse start = quizService.startSession(deck.getId(), user.getId(), startReq(5));
        StartSessionResponse.QuestionDto q = start.getQuestions().get(0);
        QuizQuestion question = quizQuestionRepository.findById(q.getQuestionId()).orElseThrow();
        String correct = question.getCorrectAnswer();

        SubmitToSessionRequest req = new SubmitToSessionRequest();
        req.setQuestionId(q.getQuestionId());
        req.setSelectedAnswer("  " + correct.toUpperCase() + "  ");        // 공백 + 대소문자 변형

        SubmitToSessionResponse res = quizService.submitAnswerToSession(start.getSessionId(), user.getId(), req);
        assertTrue(res.isCorrect(), "공백/대소문자 무시하고 정답 처리되어야");
    }

    // ─────────────────────────────────────────────────────────────
    // 5. 자동 세션 종료 — 마지막 문제 풀면 endedAt 채워짐
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("submit - 마지막 문제 풀면 세션 자동 종료 (endedAt 채워짐)")
    void submitAnswer_lastQuestion_autoEndsSession() {
        for (int i = 0; i < 5; i++) addCard("X" + i, "Y" + i);
        em.flush();

        StartSessionResponse start = quizService.startSession(deck.getId(), user.getId(), startReq(3));

        for (StartSessionResponse.QuestionDto qDto : start.getQuestions()) {
            QuizQuestion q = quizQuestionRepository.findById(qDto.getQuestionId()).orElseThrow();
            SubmitToSessionRequest req = new SubmitToSessionRequest();
            req.setQuestionId(qDto.getQuestionId());
            req.setSelectedAnswer(q.getCorrectAnswer());
            quizService.submitAnswerToSession(start.getSessionId(), user.getId(), req);
        }

        em.flush();
        em.clear();
        QuizSession session = quizSessionRepository.findById(start.getSessionId()).orElseThrow();
        assertNotNull(session.getEndedAt(), "마지막 문제 풀면 endedAt 자동 채워져야");
    }

    // ─────────────────────────────────────────────────────────────
    // 6. 재제출 차단 — 같은 문제 두 번 답하면 거부
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("submit - 같은 문제 재제출 거부 (조작 방지)")
    void submitAnswer_rejectsReSubmit() {
        for (int i = 0; i < 5; i++) addCard("P" + i, "Q" + i);
        em.flush();

        StartSessionResponse start = quizService.startSession(deck.getId(), user.getId(), startReq(5));
        StartSessionResponse.QuestionDto qDto = start.getQuestions().get(0);

        SubmitToSessionRequest req = new SubmitToSessionRequest();
        req.setQuestionId(qDto.getQuestionId());
        req.setSelectedAnswer("anything");

        quizService.submitAnswerToSession(start.getSessionId(), user.getId(), req);  // 첫 제출 OK

        assertThrows(BadRequestException.class, () ->
                quizService.submitAnswerToSession(start.getSessionId(), user.getId(), req),
                "재제출은 BadRequest");
    }

    // ─────────────────────────────────────────────────────────────
    // 7. 정답 노출 방지 — 안 푼 문제의 correctAnswer는 NULL
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("getSummary - 안 푼 문제 정답은 NULL (정답 노출 방지)")
    void getSummary_hidesAnswersOfUnanswered() {
        for (int i = 0; i < 5; i++) addCard("M" + i, "N" + i);
        em.flush();

        StartSessionResponse start = quizService.startSession(deck.getId(), user.getId(), startReq(5));
        StartSessionResponse.QuestionDto first = start.getQuestions().get(0);
        QuizQuestion firstQ = quizQuestionRepository.findById(first.getQuestionId()).orElseThrow();

        SubmitToSessionRequest req = new SubmitToSessionRequest();
        req.setQuestionId(first.getQuestionId());
        req.setSelectedAnswer(firstQ.getCorrectAnswer());
        quizService.submitAnswerToSession(start.getSessionId(), user.getId(), req);   // 첫 문제만 풀기

        em.flush();
        em.clear();
        SessionSummaryResponse summary = quizService.getSummary(start.getSessionId(), user.getId());

        for (SessionSummaryResponse.QuestionResult r : summary.getQuestions()) {
            if (r.getQuestionId().equals(first.getQuestionId())) {
                assertNotNull(r.getCorrectAnswer(), "푼 문제는 정답 공개");
            } else {
                assertNull(r.getCorrectAnswer(), "안 푼 문제는 정답 NULL");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 7. "이번 오답 다시" — sourceSessionId (Codex 검산 2026-08-23: 장부 불일치 수리)
    // ─────────────────────────────────────────────────────────────

    /** 첫 문제를 일부러 틀리고 나머지는 맞힌 뒤, 틀린 카드의 문제 텍스트를 돌려준다 */
    private String answerFirstWrongRestRight(StartSessionResponse start) {
        String wrongQuestionText = null;
        for (int i = 0; i < start.getQuestions().size(); i++) {
            StartSessionResponse.QuestionDto q = start.getQuestions().get(i);
            QuizQuestion question = quizQuestionRepository.findById(q.getQuestionId()).orElseThrow();
            String correct = question.getCorrectAnswer();
            String pick = correct;
            if (i == 0) {
                pick = q.getChoices().stream().filter(c -> !c.equals(correct)).findFirst().orElseThrow();
                wrongQuestionText = q.getQuestion();
            }
            SubmitToSessionRequest req = new SubmitToSessionRequest();
            req.setQuestionId(q.getQuestionId());
            req.setSelectedAnswer(pick);
            quizService.submitAnswerToSession(start.getSessionId(), user.getId(), req);
        }
        return wrongQuestionText;
    }

    @Test
    @DisplayName("이번 오답 다시 — 오답이 1장뿐이어도 재시험 가능, 선택지는 덱 전체에서")
    void retryFromSession_singleWrongCard_includesIt() {
        for (int i = 0; i < 6; i++) addCard("front" + i, "back" + i);
        em.flush();
        StartSessionResponse first = quizService.startSession(deck.getId(), user.getId(), startReq(6));
        String wrongText = answerFirstWrongRestRight(first);

        StartSessionRequest retry = startReq(10);
        retry.setSourceSessionId(first.getSessionId());
        StartSessionResponse second = quizService.startSession(deck.getId(), user.getId(), retry);

        assertEquals(1, second.getTotal(), "틀린 카드 1장만 출제");
        assertEquals(wrongText, second.getQuestions().get(0).getQuestion(), "방금 틀린 그 카드");
        assertTrue(second.getQuestions().get(0).getChoices().size() >= 2,
                "출제 풀이 1장이어도 오답지는 덱 전체에서 — 예전 구조면 '최소 2개' 400");
    }

    @Test
    @DisplayName("누적 오답(wrongOnly)도 세션 장부의 오답을 본다 — quiz_attempts만 보던 불일치 수리")
    void wrongOnly_seesSessionWrongs() {
        for (int i = 0; i < 6; i++) addCard("front" + i, "back" + i);
        em.flush();
        StartSessionResponse first = quizService.startSession(deck.getId(), user.getId(), startReq(6));
        String wrongText = answerFirstWrongRestRight(first);

        StartSessionRequest req = startReq(10);
        req.setWrongOnly(true);
        StartSessionResponse res = quizService.startSession(deck.getId(), user.getId(), req);

        assertEquals(1, res.getTotal());
        assertEquals(wrongText, res.getQuestions().get(0).getQuestion());
    }

    @Test
    @DisplayName("남의 sourceSessionId는 403 — 세션 소유자 검증")
    void retryFromSession_othersSession_forbidden() {
        for (int i = 0; i < 3; i++) addCard("front" + i, "back" + i);
        em.flush();
        StartSessionResponse mine = quizService.startSession(deck.getId(), user.getId(), startReq(3));
        answerFirstWrongRestRight(mine);

        User other = userRepository.save(User.builder()
                .email("other-quiz@test.com").password("encoded").nickname("남").build());
        Deck othersDeck = deckRepository.save(Deck.builder().title("남의 덱").user(other).build());
        cardRepository.save(Card.builder().front("x").back("y").deck(othersDeck).build());
        cardRepository.save(Card.builder().front("p").back("q").deck(othersDeck).build());
        em.flush();

        StartSessionRequest retry = startReq(5);
        retry.setSourceSessionId(mine.getSessionId());
        assertThrows(ForbiddenException.class,
                () -> quizService.startSession(othersDeck.getId(), other.getId(), retry));
    }

    @Test
    @DisplayName("total이 0 이하면 400")
    void startSession_totalZero_badRequest() {
        for (int i = 0; i < 3; i++) addCard("front" + i, "back" + i);
        em.flush();
        assertThrows(BadRequestException.class,
                () -> quizService.startSession(deck.getId(), user.getId(), startReq(0)));
        assertThrows(BadRequestException.class,
                () -> quizService.startSession(deck.getId(), user.getId(), startReq(-3)));
    }
}
