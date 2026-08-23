package com.vocamaster.quiz;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.quiz.dto.StartSessionRequest;
import com.vocamaster.quiz.dto.StartSessionResponse;
import com.vocamaster.quiz.dto.SubmitToSessionRequest;
import com.vocamaster.stats.DailyUserStatRepository;
import com.vocamaster.typing.*;
import com.vocamaster.typing.dto.StartTypingSessionRequest;
import com.vocamaster.typing.dto.StartTypingSessionResponse;
import com.vocamaster.typing.dto.SubmitTypedAnswerRequest;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 답 제출 직렬화 — 세션 행 X 잠금 (Codex 검산 2026-08-23).
 * 잠금 없이는 "확인→저장→종료판정" 사이에 다른 제출이 끼어들어
 *  ① 같은 문제가 두 번 반영(마지막 답이 덮어씀) ② 마지막 두 문제 동시 제출 시 둘 다 "아직 안 끝났네" → endedAt 누락.
 * NOT_SUPPORTED: 스레드들은 커밋된 데이터만 본다 (DeckCopyConcurrencyTest와 동일 이유).
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SessionSubmitConcurrencyTest extends AbstractIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired private QuizService quizService;
    @Autowired private TypingService typingService;
    @Autowired private QuizSessionRepository quizSessionRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;
    @Autowired private TypingSessionRepository typingSessionRepository;
    @Autowired private TypingQuestionRepository typingQuestionRepository;
    @Autowired private DailyUserStatRepository dailyUserStatRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private UserRepository userRepository;

    private User user;
    private Deck deck;

    @BeforeEach
    void setUp() {
        long tag = System.nanoTime();
        user = userRepository.save(User.builder()
                .email("submit_" + tag + "@test.com").password("encoded").nickname("동시제출").build());
        deck = deckRepository.save(Deck.builder().title("submit " + tag).user(user).build());
        for (int i = 0; i < 4; i++) {
            cardRepository.save(Card.builder().front("f" + i).back("b" + i).deck(deck).build());
        }
    }

    @AfterEach
    void cleanUp() {
        // FK 역순: 문제 → 세션 → 카드 → 덱 → 출석 → 사용자
        for (QuizSession s : quizSessionRepository.findAll()) {
            if (s.getDeck().getId().equals(deck.getId())) {
                quizQuestionRepository.deleteAll(quizQuestionRepository.findBySessionIdOrderByQuestionOrderAsc(s.getId()));
                quizSessionRepository.delete(s);
            }
        }
        for (TypingSession s : typingSessionRepository.findAll()) {
            if (s.getDeck().getId().equals(deck.getId())) {
                typingQuestionRepository.deleteAll(typingQuestionRepository.findBySessionIdOrderByQuestionOrderAsc(s.getId()));
                typingSessionRepository.delete(s);
            }
        }
        cardRepository.deleteAll(cardRepository.findByDeckId(deck.getId()));
        deckRepository.delete(deck);
        dailyUserStatRepository.findByUserIdAndStatDate(user.getId(), LocalDate.now(KST))
                .ifPresent(dailyUserStatRepository::delete);
        userRepository.delete(user);
    }

    /** 작업들을 동시에 출발시키고, 성공 횟수를 돌려준다 (예외는 실패로 셈) */
    private int runConcurrently(List<Runnable> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        List<Future<?>> fs = new java.util.ArrayList<>();
        for (Runnable t : tasks) {
            fs.add(pool.submit(() -> {
                try { go.await(); t.run(); ok.incrementAndGet(); } catch (Exception ignored) { }
            }));
        }
        go.countDown();
        for (Future<?> f : fs) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        return ok.get();
    }

    private StartSessionRequest quizReq(int total) {
        StartSessionRequest r = new StartSessionRequest();
        r.setDirection("front_to_back");
        r.setTotal(total);
        return r;
    }

    private SubmitToSessionRequest quizAnswer(Long questionId, String answer) {
        SubmitToSessionRequest r = new SubmitToSessionRequest();
        r.setQuestionId(questionId);
        r.setSelectedAnswer(answer);
        return r;
    }

    @Test
    @DisplayName("퀴즈: 같은 문제 동시 2회 제출 → 정확히 1회만 반영 (나머지는 '이미 답한 문제')")
    void quiz_sameQuestionTwice_onlyOnce() throws Exception {
        StartSessionResponse s = quizService.startSession(deck.getId(), user.getId(), quizReq(4));
        Long qid = s.getQuestions().get(0).getQuestionId();
        int ok = runConcurrently(List.of(
                () -> quizService.submitAnswerToSession(deck.getId(), s.getSessionId(), user.getId(), quizAnswer(qid, "first")),
                () -> quizService.submitAnswerToSession(deck.getId(), s.getSessionId(), user.getId(), quizAnswer(qid, "second"))));
        assertEquals(1, ok, "한 번만 성공");
        QuizQuestion q = quizQuestionRepository.findById(qid).orElseThrow();
        assertTrue(List.of("first", "second").contains(q.getSelectedAnswer()));
        assertEquals(1, dailyUserStatRepository.findByUserIdAndStatDate(user.getId(), LocalDate.now(KST))
                .orElseThrow().getStudyCount(), "출석도 1회만");
    }

    @Test
    @DisplayName("퀴즈: 마지막 두 문제 동시 제출 → endedAt 반드시 기록")
    void quiz_lastTwoConcurrently_sessionEnds() throws Exception {
        StartSessionResponse s = quizService.startSession(deck.getId(), user.getId(), quizReq(2));
        Long q1 = s.getQuestions().get(0).getQuestionId();
        Long q2 = s.getQuestions().get(1).getQuestionId();
        int ok = runConcurrently(List.of(
                () -> quizService.submitAnswerToSession(deck.getId(), s.getSessionId(), user.getId(), quizAnswer(q1, "x")),
                () -> quizService.submitAnswerToSession(deck.getId(), s.getSessionId(), user.getId(), quizAnswer(q2, "y"))));
        assertEquals(2, ok);
        assertNotNull(quizSessionRepository.findById(s.getSessionId()).orElseThrow().getEndedAt(),
                "둘 다 '상대가 아직 안 끝났네'로 판단하면 endedAt이 영원히 null — 잠금이 막는 사고");
    }

    private StartTypingSessionRequest typingReq(int total) {
        StartTypingSessionRequest r = new StartTypingSessionRequest();
        r.setDirection("front_to_back");
        r.setTotal(total);
        return r;
    }

    private SubmitTypedAnswerRequest typed(Long questionId, String text) {
        SubmitTypedAnswerRequest r = new SubmitTypedAnswerRequest();
        r.setQuestionId(questionId);
        r.setTypedAnswer(text);
        return r;
    }

    @Test
    @DisplayName("타이핑: 같은 문제 동시 2회 → 1회만, 마지막 두 문제 동시 → endedAt 기록")
    void typing_serialized() throws Exception {
        StartTypingSessionResponse s = typingService.startSession(deck.getId(), user.getId(), typingReq(2));
        Long q1 = s.getQuestions().get(0).getQuestionId();
        Long q2 = s.getQuestions().get(1).getQuestionId();

        int ok = runConcurrently(List.of(
                () -> typingService.submitTypedAnswer(deck.getId(), s.getSessionId(), user.getId(), typed(q1, "a")),
                () -> typingService.submitTypedAnswer(deck.getId(), s.getSessionId(), user.getId(), typed(q1, "b"))));
        assertEquals(1, ok, "같은 문제는 한 번만");

        ok = runConcurrently(List.of(
                () -> typingService.submitTypedAnswer(deck.getId(), s.getSessionId(), user.getId(), typed(q2, "c")),
                () -> typingService.submitTypedAnswer(deck.getId(), s.getSessionId(), user.getId(), typed(q2, "d"))));
        assertEquals(1, ok);
        assertNotNull(typingSessionRepository.findById(s.getSessionId()).orElseThrow().getEndedAt(), "전부 풀렸으니 종료");
    }
}
