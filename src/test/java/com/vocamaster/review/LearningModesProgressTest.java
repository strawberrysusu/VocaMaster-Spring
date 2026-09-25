package com.vocamaster.review;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.card.CardService;
import com.vocamaster.card.dto.CardResponse;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.common.exception.ForbiddenException;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.quiz.QuizQuestion;
import com.vocamaster.quiz.QuizQuestionRepository;
import com.vocamaster.quiz.QuizService;
import com.vocamaster.quiz.dto.StartSessionRequest;
import com.vocamaster.quiz.dto.SubmitToSessionRequest;
import com.vocamaster.review.dto.BatchAnswerRequest;
import com.vocamaster.stats.DailyUserStatRepository;
import com.vocamaster.study.StudyRecordRepository;
import com.vocamaster.study.StudyService;
import com.vocamaster.study.dto.RecordStudyRequest;
import com.vocamaster.study.dto.StartStudyRequest;
import com.vocamaster.study.dto.StudyRecordResponse;
import com.vocamaster.typing.TypingQuestion;
import com.vocamaster.typing.TypingQuestionRepository;
import com.vocamaster.typing.TypingService;
import com.vocamaster.typing.dto.StartTypingSessionRequest;
import com.vocamaster.typing.dto.SubmitTypedAnswerRequest;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 실제 채점 서비스와 DB를 거쳐 모드 공통 진도 및 카드 조회 응답을 검증한다. */
class LearningModesProgressTest extends AbstractIntegrationTest {

    @Autowired private ReviewService reviewService;
    @Autowired private QuizService quizService;
    @Autowired private TypingService typingService;
    @Autowired private StudyService studyService;
    @Autowired private CardService cardService;
    @Autowired private CardProgressRepository progressRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;
    @Autowired private TypingQuestionRepository typingQuestionRepository;
    @Autowired private StudyRecordRepository studyRecordRepository;
    @Autowired private DailyUserStatRepository dailyUserStatRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private CardRepository cardRepository;
    @PersistenceContext private EntityManager em;

    private Long userId;
    private Long deckId;
    private Long cardId;

    enum Mode { QUIZ, TYPING }

    private record Question(Long sessionId, Long questionId, String correctAnswer) {}

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .email("learning-modes@test.com").password("encoded").nickname("learner").build());
        Deck deck = deckRepository.save(Deck.builder().title("Shared progress").user(user).build());
        Card card = cardRepository.save(Card.builder().front("apple").back("사과").deck(deck).build());
        // 2개 문제를 만들면 대상 카드에 답한 뒤에도 세션이 열려 있어 중복 문항 검증을 직접 거친다.
        cardRepository.save(Card.builder().front("banana").back("바나나").deck(deck).build());
        userId = user.getId();
        deckId = deck.getId();
        cardId = card.getId();
        flushAndClear();
    }

    @Test
    @DisplayName("복습·퀴즈·타이핑 정답을 합산해 3연속 알아요, 오답 후 다시 3연속 필요")
    void answersAcrossModesReachKnownAndRecoverAfterWrong() {
        assertEquals(3, LearningStatus.KNOWN_STREAK, "사용자 정책: 3연속 정답");
        assertEquals(LearningStatus.UNKNOWN, cardService.findOne(cardId, userId).getLearningStatus());
        assertTrue(progressRepository.findByUserIdAndCardId(userId, cardId).isEmpty());

        reviewAnswer(true);
        assertProgress(1, 0, LearningStatus.UNKNOWN, 1);

        answer(Mode.QUIZ, true);
        assertProgress(2, 0, LearningStatus.UNKNOWN, 2);

        answer(Mode.TYPING, true);
        assertProgress(3, 0, LearningStatus.KNOWN, 3);

        answer(Mode.QUIZ, false);
        assertProgress(0, 1, LearningStatus.UNKNOWN, 4);

        answer(Mode.TYPING, true);
        assertProgress(1, 1, LearningStatus.UNKNOWN, 5);
        reviewAnswer(true);
        assertProgress(2, 1, LearningStatus.UNKNOWN, 6);
        answer(Mode.QUIZ, true);
        assertProgress(3, 1, LearningStatus.KNOWN, 7);
    }

    @ParameterizedTest(name = "{0}: 오답은 알아요를 즉시 초기화")
    @EnumSource(Mode.class)
    void gradedWrongAnswerResetsKnown(Mode mode) {
        reviewAnswer(true);
        reviewAnswer(true);
        reviewAnswer(true);
        assertProgress(3, 0, LearningStatus.KNOWN, 3);

        answer(mode, false);

        CardProgress progress = assertProgress(0, 1, LearningStatus.UNKNOWN, 4);
        assertEquals(1, progress.getBoxLevel(), "오답은 복습 간격도 첫 박스로 초기화");
    }

    @ParameterizedTest(name = "{0}: 재제출은 진도·답변 수를 늘리지 않음")
    @EnumSource(Mode.class)
    void duplicateAnswerDoesNotChangeProgressOrStatistics(Mode mode) {
        Question question = startQuestion(mode);
        assertTrue(submit(mode, question, userId, deckId, true));
        CardProgress before = assertProgress(1, 0, LearningStatus.UNKNOWN, 1);

        assertThrows(BadRequestException.class,
                () -> submit(mode, question, userId, deckId, false));

        CardProgress after = assertProgress(1, 0, LearningStatus.UNKNOWN, 1);
        assertEquals(before.getVersion(), after.getVersion());
        assertEquals(before.getLastReviewedAt(), after.getLastReviewedAt());
        assertEquals(before.getNextReviewAt(), after.getNextReviewAt());
        assertStoredAnswer(mode, question, true);
    }

    @ParameterizedTest(name = "{0}: 타인·다른 덱 경로 제출은 학습 기록을 바꾸지 않음")
    @EnumSource(Mode.class)
    void unauthorizedAndWrongDeckAnswersDoNotChangeProgress(Mode mode) {
        reviewAnswer(true);
        Question question = startQuestion(mode);
        Long otherUserId = userRepository.save(User.builder()
                .email("learning-other@test.com").password("encoded").nickname("other").build()).getId();
        Long otherDeckId = deckRepository.save(Deck.builder().title("Other deck")
                .user(userRepository.findById(userId).orElseThrow()).build()).getId();
        CardProgress before = assertProgress(1, 0, LearningStatus.UNKNOWN, 1);

        assertThrows(ForbiddenException.class,
                () -> submit(mode, question, otherUserId, deckId, false));
        assertThrows(BadRequestException.class,
                () -> submit(mode, question, userId, otherDeckId, false));

        CardProgress after = assertProgress(1, 0, LearningStatus.UNKNOWN, 1);
        assertEquals(before.getVersion(), after.getVersion());
        assertEquals(before.getLastReviewedAt(), after.getLastReviewedAt());
        assertEquals(before.getNextReviewAt(), after.getNextReviewAt());
        assertTrue(progressRepository.findByUserIdAndCardId(otherUserId, cardId).isEmpty());
        assertEquals(0, studyCount(otherUserId));
        if (mode == Mode.QUIZ) {
            QuizQuestion stored = quizQuestionRepository.findById(question.questionId()).orElseThrow();
            assertNull(stored.getAnsweredAt());
            assertNull(stored.getIsCorrect());
        } else {
            TypingQuestion stored = typingQuestionRepository.findById(question.questionId()).orElseThrow();
            assertNull(stored.getAnsweredAt());
            assertNull(stored.getIsCorrect());
        }
    }

    @Test
    @DisplayName("구형 플래시카드 안다·모른다 제출도 공통 진도와 통계에 한 번씩 반영")
    void legacyStudyRecordsUpdateSharedProgress() {
        StartStudyRequest start = new StartStudyRequest();
        start.setDirection("front_to_back");
        Long sessionId = studyService.startSession(deckId, userId, start).getSessionId();
        RecordStudyRequest answer = new RecordStudyRequest();
        answer.setCardId(cardId);
        answer.setKnown(true);

        StudyRecordResponse known = studyService.recordAnswer(sessionId, userId, answer);
        assertProgress(1, 0, LearningStatus.UNKNOWN, 1);
        assertTrue(studyRecordRepository.findById(known.getId()).orElseThrow().getKnown());

        answer.setKnown(false);
        StudyRecordResponse unknown = studyService.recordAnswer(sessionId, userId, answer);
        assertProgress(0, 1, LearningStatus.UNKNOWN, 2);
        assertFalse(studyRecordRepository.findById(unknown.getId()).orElseThrow().getKnown());
    }

    private void reviewAnswer(boolean correct) {
        BatchAnswerRequest.Item item = new BatchAnswerRequest.Item();
        item.setCardId(cardId);
        item.setCorrect(correct);
        BatchAnswerRequest request = new BatchAnswerRequest();
        request.setSubmissionId(UUID.randomUUID().toString());
        request.setAnswers(List.of(item));
        reviewService.recordAnswers(userId, request);
    }

    private void answer(Mode mode, boolean correct) {
        Question question = startQuestion(mode);
        assertEquals(correct, submit(mode, question, userId, deckId, correct));
        flushAndClear();
        assertStoredAnswer(mode, question, correct);
    }

    private Question startQuestion(Mode mode) {
        if (mode == Mode.QUIZ) {
            StartSessionRequest request = new StartSessionRequest();
            request.setDirection("front_to_back");
            request.setTotal(2);
            Long sessionId = quizService.startSession(deckId, userId, request).getSessionId();
            QuizQuestion question = quizQuestionRepository.findBySessionIdOrderByQuestionOrderAsc(sessionId)
                    .stream().filter(q -> q.getCard().getId().equals(cardId)).findFirst().orElseThrow();
            return new Question(sessionId, question.getId(), question.getCorrectAnswer());
        }
        StartTypingSessionRequest request = new StartTypingSessionRequest();
        request.setDirection("front_to_back");
        request.setTotal(2);
        Long sessionId = typingService.startSession(deckId, userId, request).getSessionId();
        TypingQuestion question = typingQuestionRepository.findBySessionIdOrderByQuestionOrderAsc(sessionId)
                .stream().filter(q -> q.getCard().getId().equals(cardId)).findFirst().orElseThrow();
        return new Question(sessionId, question.getId(), question.getCorrectAnswer());
    }

    private boolean submit(Mode mode, Question question, Long submittedUserId, Long submittedDeckId,
                           boolean correct) {
        String answer = correct ? question.correctAnswer() : "틀린 답";
        if (mode == Mode.QUIZ) {
            SubmitToSessionRequest request = new SubmitToSessionRequest();
            request.setQuestionId(question.questionId());
            request.setSelectedAnswer(answer);
            return quizService.submitAnswerToSession(submittedDeckId, question.sessionId(), submittedUserId, request)
                    .isCorrect();
        }
        SubmitTypedAnswerRequest request = new SubmitTypedAnswerRequest();
        request.setQuestionId(question.questionId());
        request.setTypedAnswer(answer);
        return typingService.submitTypedAnswer(submittedDeckId, question.sessionId(), submittedUserId, request)
                .isCorrect();
    }

    private void assertStoredAnswer(Mode mode, Question question, boolean correct) {
        if (mode == Mode.QUIZ) {
            QuizQuestion stored = quizQuestionRepository.findById(question.questionId()).orElseThrow();
            assertEquals(correct, stored.getIsCorrect());
            assertNotNull(stored.getAnsweredAt());
            assertEquals(correct ? question.correctAnswer() : "틀린 답", stored.getSelectedAnswer());
        } else {
            TypingQuestion stored = typingQuestionRepository.findById(question.questionId()).orElseThrow();
            assertEquals(correct, stored.getIsCorrect());
            assertNotNull(stored.getAnsweredAt());
            assertEquals(correct ? question.correctAnswer() : "틀린 답", stored.getTypedAnswer());
        }
    }

    private CardProgress assertProgress(int streak, int wrongCount, LearningStatus status, long answerCount) {
        flushAndClear();
        CardProgress progress = progressRepository.findByUserIdAndCardId(userId, cardId).orElseThrow();
        assertEquals(streak, progress.getCorrectStreak());
        assertEquals(wrongCount, progress.getWrongCount());
        assertNotNull(progress.getLastReviewedAt());
        assertNotNull(progress.getNextReviewAt());
        CardResponse card = cardService.findOne(cardId, userId);
        assertEquals(status, card.getLearningStatus(), "덱에서 다시 읽는 카드 상태도 저장된 진도와 같아야 함");
        assertEquals(streak, card.getCorrectStreak());
        assertEquals(wrongCount, card.getWrongCount());
        assertEquals(answerCount, studyCount(userId), "각 답변은 오늘 통계에 정확히 한 번만 더해야 함");
        return progress;
    }

    private long studyCount(Long targetUserId) {
        // KST 자정에 걸려도 답변 수 검증이 흔들리지 않도록 해당 테스트 사용자의 누적 수를 조회한다.
        return ((Number) dailyUserStatRepository.aggregate(targetUserId).get(0)[0]).longValue();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
