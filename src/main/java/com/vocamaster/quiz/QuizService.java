package com.vocamaster.quiz;

import com.vocamaster.stats.StatsService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.card.dto.CardResponse;
import com.vocamaster.common.Direction;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.common.exception.ForbiddenException;
import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckService;
import com.vocamaster.quiz.dto.*;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;


@Service
@RequiredArgsConstructor
public class QuizService {

    private static final int DEFAULT_TOTAL = 10;
    private static final int DEFAULT_CHOICES = 4;    // 세션 API (React 퀴즈) 기본값
    private static final int MIN_CHOICES = 2;
    private static final int MAX_CHOICES = 6;        // 사용자 선택 상한 (2026-08-25 요청: 한국 시험 스타일 5지, 최대 6지)
    private static final int LEGACY_CHOICES = 5;     // 구형 단건 API (Mustache) — 기존 계약 유지

    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizSessionRepository quizSessionRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final CardRepository cardRepository;
    private final DeckService deckService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final StatsService statsService;

    // 5지선다 퀴즈 문제 생성 (정답은 서버만 알고 있음)
    public QuizQuestionResponse generateQuiz(Long deckId, Long userId, GenerateQuizRequest req) {
        deckService.verifyOwner(deckId, userId);

        List<Card> cards;

        if (Boolean.TRUE.equals(req.getWrongOnly())) {
            List<Long> wrongIds = quizAttemptRepository.findWrongCardIds(deckId, userId);
            if (wrongIds.isEmpty()) {
                throw new BadRequestException("오답 카드가 없습니다");
            }
            cards = cardRepository.findAllById(wrongIds).stream()
                    .filter(c -> c.getDeck().getId().equals(deckId))
                    .toList();
        } else if (Boolean.TRUE.equals(req.getStarredOnly())) {
            cards = cardRepository.findByDeckIdAndStarredTrue(deckId);
        } else {
            cards = cardRepository.findByDeckId(deckId);
        }

        if (cards.size() < 5) {
            throw new BadRequestException
                    ("퀴즈를 만들려면 최소 5개의 카드가 필요합니다. 현재 " + cards.size() + "개입니다.");
        }

        Direction direction = Direction.from(req.getDirection());

        List<Card> shuffled = new ArrayList<>(cards);
        Collections.shuffle(shuffled);
        Card questionCard = shuffled.get(0);


        String question = direction.isFrontToBack() ? questionCard.getFront() : questionCard.getBack();
        String correctAnswer = direction.isFrontToBack() ? questionCard.getBack() : questionCard.getFront();

        // 구형 단건 API(Mustache 화면용) — 선택지 중복 제거도 세션 API와 같은 자(buildChoices/normalizeAnswer)로.
        // React 퀴즈(세션 API)가 이 경로를 대체하므로 deprecated 후보 (체크리스트 기록, 2026-08-23)
        List<String> choices = buildChoices(cards, questionCard, correctAnswer, direction, LEGACY_CHOICES);   // 구형은 5지선다 유지

        return QuizQuestionResponse.builder()
                .cardId(questionCard.getId())
                .question(question)
                .choices(choices)
                .direction(req.getDirection())
                .build();
    }

    // 퀴즈 답안 제출 — 서버에서 정답 판정
    @Transactional  // 답변 저장 + 출석 도장이 같이 성공하거나 같이 롤백 (2026-07-27, Codex 검산 반영)
    public QuizResultResponse submitAnswer(Long deckId, Long userId, SubmitQuizRequest req) {
        deckService.verifyOwner(deckId, userId);

        Card card = cardRepository.findById(req.getCardId())
                .orElseThrow(() -> new NotFoundException("카드를 찾을 수 없습니다"));

        if (!card.getDeck().getId().equals(deckId)) {
            throw new BadRequestException("이 덱에 속하지 않는 카드입니다");
        }

        Direction dir = Direction.from(req.getDirection());
        String correctAnswer = dir.isFrontToBack() ? card.getBack() : card.getFront();
        boolean isCorrect = normalizeAnswer(req.getSelectedAnswer()).equals(normalizeAnswer(correctAnswer));   // 세션 채점과 같은 자

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다"));

        QuizAttempt attempt = QuizAttempt.builder()
                .deckId(deckId)
                .user(user)
                .card(card)
                .direction(req.getDirection())
                .selectedAnswer(req.getSelectedAnswer())
                .correctAnswer(correctAnswer)
                .isCorrect(isCorrect)
                .build();

        quizAttemptRepository.save(attempt);
        statsService.recordStudy(userId, deckId);   // 출석 도장 (연속 학습일)

        return QuizResultResponse.builder()
                .id(attempt.getId())
                .correct(isCorrect)
                .correctAnswer(correctAnswer)
                .selectedAnswer(req.getSelectedAnswer())
                .build();
    }

    // 퀴즈 기록 조회
    public QuizHistoryResponse getHistory(Long deckId, Long userId) {
        deckService.verifyOwner(deckId, userId);

        var attempts = quizAttemptRepository.findTop50ByDeckIdAndUserIdOrderByCreatedAtDesc(deckId, userId);
        long total = attempts.size();
        long correct = attempts.stream().filter(QuizAttempt::getIsCorrect).count();

        List<QuizHistoryResponse.AttemptDto> attemptDtos = attempts.stream()
                .map(a -> QuizHistoryResponse.AttemptDto.builder()
                        .id(a.getId())
                        .cardId(a.getCard().getId())
                        .direction(a.getDirection())
                        .selectedAnswer(a.getSelectedAnswer())
                        .correctAnswer(a.getCorrectAnswer())
                        .correct(a.getIsCorrect())
                        .createdAt(a.getCreatedAt())
                        .build())
                .toList();

        return QuizHistoryResponse.builder()
                .attempts(attemptDtos)
                .total(total)
                .correct(correct)
                .wrong(total - correct)
                .accuracy(total > 0 ? Math.round((double) correct / total * 100) : 0)
                .build();
    }

    // 오답 카드 목록
    public Map<String, Object> getWrongCards(Long deckId, Long userId) {
        deckService.verifyOwner(deckId, userId);

        List<Long> wrongIds = quizAttemptRepository.findWrongCardIds(deckId, userId);
        if (wrongIds.isEmpty()) {
            return Map.of("cards", List.of(), "total", 0);
        }

        List<CardResponse> cards = cardRepository.findAllById(wrongIds).stream()
                .filter(c -> c.getDeck().getId().equals(deckId))
                .map(CardResponse::from)
                .toList();

        return Map.of("cards", cards, "total", cards.size());
    }

    // ============================================================
    // ADR-024: 퀴즈 세션 단위 관리 (Eager 생성)
    // ============================================================

    /**
     * 세션 시작 — N문제 미리 생성 후 저장. 시작 후 카드 변동과 무관하게 그 세션은 고정.
     */
    @Transactional
    public StartSessionResponse startSession(Long deckId, Long userId, StartSessionRequest req) {
        Deck deck = deckService.verifyOwner(deckId, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다"));

        if (req.getTotal() != null && req.getTotal() <= 0) {
            throw new BadRequestException("total은 1 이상이어야 합니다. 입력값: " + req.getTotal());
        }

        // 1) 풀 두 개를 분리 (Codex 검산 2026-08-23):
        //    - distractorPool(오답지 재료) = 덱 전체 → 2장 이상이면 오답지를 만들 수 있다
        //    - questionPool(출제 대상)     = 전체 / 이번 세션 오답 / 누적 오답 / 즐겨찾기 → 1장이어도 출제 가능
        //    예전엔 둘이 같은 풀이라 '오답 1장'이면 재시험 자체가 불가능했다
        List<Card> distractorPool = cardRepository.findByDeckId(deckId);
        if (distractorPool.size() < 2) {
            throw new BadRequestException(
                    "퀴즈에는 덱에 카드가 최소 2개 필요합니다. 현재 " + distractorPool.size() + "개");
        }

        List<Card> pool;
        if (req.getSourceSessionId() != null) {
            pool = wrongCardsOfSession(req.getSourceSessionId(), deckId, userId);
        } else if (Boolean.TRUE.equals(req.getWrongOnly())) {
            // 누적 오답 = 세션 장부(quiz_questions) ∪ 구형 단건 장부(quiz_attempts) — 두 장부 불일치 수리
            Set<Long> wrongIds = new LinkedHashSet<>(quizQuestionRepository.findWrongCardIds(deckId, userId));
            wrongIds.addAll(quizAttemptRepository.findWrongCardIds(deckId, userId));
            if (wrongIds.isEmpty()) throw new BadRequestException("오답 카드가 없습니다");
            pool = cardRepository.findAllById(wrongIds).stream()
                    .filter(c -> c.getDeck().getId().equals(deckId))
                    .toList();
        } else if (Boolean.TRUE.equals(req.getStarredOnly())) {
            pool = cardRepository.findByDeckIdAndStarredTrue(deckId);
        } else {
            pool = distractorPool;
        }
        if (pool.isEmpty()) {
            throw new BadRequestException("출제할 카드가 없습니다");
        }

        Direction direction = Direction.from(req.getDirection());

        // 이 방향에서 '서로 다른 답'이 2개 미만이면 오답지를 만들 수 없다 — 카드 수가 아니라 답의 종류가 기준
        // (뜻이 전부 "과일"인 덱: 카드 5장이어도 단어→뜻 방향은 선택지가 정답 하나뿐)
        long distinctAnswers = distractorPool.stream()
                .map(c -> normalizeAnswer(answerOf(c, direction)))
                .distinct().count();
        if (distinctAnswers < 2) {
            throw new BadRequestException("이 방향에서는 서로 다른 답이 2개 미만이라 선택지를 만들 수 없습니다 (답 종류 "
                    + distinctAnswers + "개)");
        }

        int requestedTotal = (req.getTotal() == null) ? DEFAULT_TOTAL : req.getTotal();
        int total = Math.min(requestedTotal, pool.size());                       // 카드 부족하면 그만큼만
        int requestedChoices = req.getChoiceCount() == null ? DEFAULT_CHOICES : req.getChoiceCount();
        if (requestedChoices < MIN_CHOICES || requestedChoices > MAX_CHOICES) {
            throw new BadRequestException("choiceCount는 " + MIN_CHOICES + "~" + MAX_CHOICES + " 사이여야 합니다. 입력값: " + requestedChoices);
        }
        int choiceCount = (int) Math.min(requestedChoices, distinctAnswers);    // 답 종류가 모자라면 그만큼만 (fallback)

        // 3) 세션 row 저장 (startedAt은 @CreationTimestamp 자동)
        QuizSession session = quizSessionRepository.save(QuizSession.builder()
                .user(user)
                .deck(deck)
                .direction(direction.name())
                .total(total)
                .build());

        // 4) 카드 셔플 → 출제할 N장 선정
        List<Card> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        List<Card> questionCards = shuffled.subList(0, total);

        // 5) 각 카드마다 문제 + 오답지 생성 → QuizQuestion N개 저장
        List<StartSessionResponse.QuestionDto> dtos = new ArrayList<>();
        for (int i = 0; i < questionCards.size(); i++) {
            Card qc = questionCards.get(i);
            String questionText = direction.isFrontToBack() ? qc.getFront() : qc.getBack();
            String correctAnswer = answerOf(qc, direction);

            List<String> choices = buildChoices(distractorPool, qc, correctAnswer, direction, choiceCount);

            QuizQuestion q = quizQuestionRepository.save(QuizQuestion.builder()
                    .session(session)
                    .card(qc)
                    .questionOrder(i)
                    .questionText(questionText)
                    .choicesJson(toJson(choices))
                    .correctAnswer(correctAnswer)
                    .build());

            dtos.add(StartSessionResponse.QuestionDto.builder()
                    .questionId(q.getId())
                    .questionOrder(i)
                    .question(questionText)
                    .reading(direction.isFrontToBack() ? qc.getReading() : null)
                    .choices(choices)
                    .build());
        }

        return StartSessionResponse.builder()
                .sessionId(session.getId())
                .direction(direction.name())
                .total(total)
                .questions(dtos)
                .build();
    }

    // 정답 + 오답지 (중복 제거) → 셔플
    // URL의 deckId와 세션의 덱이 다르면 거부 — 예전엔 path deckId를 무시해 덱 A 세션을 덱 B 주소로 제출할 수 있었다 (계약 위반, Codex 검산)
    private static void assertSessionDeck(QuizSession session, Long deckId) {
        if (deckId != null && !session.getDeck().getId().equals(deckId)) {
            throw new BadRequestException("이 덱의 세션이 아닙니다");
        }
    }

    /** "이번 오답 다시" 출제 풀 — 원본 세션의 소유자·덱을 검증하고, 답했는데 틀린(isCorrect=false) 카드만 */
    private List<Card> wrongCardsOfSession(Long sourceSessionId, Long deckId, Long userId) {
        QuizSession source = quizSessionRepository.findById(sourceSessionId)
                .orElseThrow(() -> new NotFoundException("세션을 찾을 수 없습니다"));
        if (!source.getUser().getId().equals(userId)) {
            throw new ForbiddenException("본인 세션이 아닙니다");
        }
        if (!source.getDeck().getId().equals(deckId)) {
            throw new BadRequestException("다른 덱의 세션입니다");
        }
        Map<Long, Card> byCard = new LinkedHashMap<>();
        for (QuizQuestion q : quizQuestionRepository.findBySessionIdOrderByQuestionOrderAsc(sourceSessionId)) {
            if (Boolean.FALSE.equals(q.getIsCorrect())) byCard.putIfAbsent(q.getCard().getId(), q.getCard());
        }
        if (byCard.isEmpty()) throw new BadRequestException("이번 세션에 오답이 없습니다");
        return new ArrayList<>(byCard.values());
    }

    /**
     * 답 비교의 단일 기준 — 선택지 중복 제거·채점·(프런트 색 표시)가 전부 이 자로 잰다.
     * 예전엔 선택지는 원문 그대로, 채점은 trim+대소문자 무시로 달라서 "Apple"/"apple"이 둘 다 선택지에 나오고
     * 둘 다 정답 처리되는 틈이 있었다 (Codex 검산 2026-08-23).
     */
    static String normalizeAnswer(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    static String answerOf(Card c, Direction d) {
        return d.isFrontToBack() ? c.getBack() : c.getFront();
    }

    private List<String> buildChoices(List<Card> pool, Card questionCard, String correctAnswer,
                                       Direction direction, int choiceCount) {
        List<Card> wrongPool = new ArrayList<>(pool);
        wrongPool.remove(questionCard);
        Collections.shuffle(wrongPool);

        Set<String> seen = new HashSet<>();
        seen.add(normalizeAnswer(correctAnswer));
        List<String> choices = new ArrayList<>();
        choices.add(correctAnswer.trim());
        for (Card w : wrongPool) {
            String wrongAns = answerOf(w, direction);
            if (seen.add(normalizeAnswer(wrongAns))) {              // 정규화 기준으로 중복 차단 (채점과 동일 자)
                choices.add(wrongAns.trim());
                if (choices.size() >= choiceCount) break;
            }
        }
        Collections.shuffle(choices);
        return choices;
    }

    private String toJson(List<String> choices) {
        try {
            return objectMapper.writeValueAsString(choices);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("choices 직렬화 실패", e);
        }
    }

    private List<String> fromJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("choices 역직렬화 실패", e);
        }
    }

    /**
     * 세션 내 한 문제 답 제출. 마지막 문제면 세션 자동 종료.
     */
    @Transactional
    public SubmitToSessionResponse submitAnswerToSession(Long deckId, Long sessionId, Long userId,
                                                          SubmitToSessionRequest req) {
        // 1) 세션 행 X 잠금 → 같은 세션의 동시 제출 직렬화 (확인→저장→종료판정 사이 끼어들기 차단)
        //    + 소유자 + URL deckId 일치(계약) + 종료 여부
        QuizSession session = quizSessionRepository.findWithLockById(sessionId)
                .orElseThrow(() -> new NotFoundException("세션을 찾을 수 없습니다"));
        if (!session.getUser().getId().equals(userId)) {
            throw new ForbiddenException("본인 세션이 아닙니다");
        }
        assertSessionDeck(session, deckId);
        if (session.getEndedAt() != null) {
            throw new BadRequestException("이미 종료된 세션입니다");
        }

        // 2) 문제 검증 (세션 소속 + 미제출)
        QuizQuestion question = quizQuestionRepository.findById(req.getQuestionId())
                .orElseThrow(() -> new NotFoundException("문제를 찾을 수 없습니다"));
        if (!question.getSession().getId().equals(sessionId)) {
            throw new BadRequestException("이 세션에 속하지 않는 문제입니다");
        }
        if (question.getAnsweredAt() != null) {
            throw new BadRequestException("이미 답한 문제입니다");
        }

        // 3) 정답 비교 — 정규화 (공백 + 대소문자 무시)
        String normalized = req.getSelectedAnswer() == null ? "" : req.getSelectedAnswer().trim();   // 저장용(표시 원문, 공백만 정리)
        boolean isCorrect = normalizeAnswer(normalized).equals(normalizeAnswer(question.getCorrectAnswer())); // 채점은 단일 기준

        // 4) 문제 row UPDATE (영속 컨텍스트에서 자동 flush)
        question.setSelectedAnswer(normalized);
        question.setIsCorrect(isCorrect);
        question.setAnsweredAt(LocalDateTime.now());

        // 5) 모든 문제 풀었나? 마지막이면 세션 endedAt 채움
        List<QuizQuestion> all = quizQuestionRepository.findBySessionIdOrderByQuestionOrderAsc(sessionId);
        boolean sessionEnded = all.stream().allMatch(q -> q.getAnsweredAt() != null);
        if (sessionEnded) {
            session.setEndedAt(LocalDateTime.now());
        }

        statsService.recordStudy(userId, session.getDeck().getId());   // 출석 도장 (연속 학습일)

        return SubmitToSessionResponse.builder()
                .correct(isCorrect)
                .correctAnswer(question.getCorrectAnswer())
                .selectedAnswer(normalized)
                .sessionEnded(sessionEnded)
                .build();
    }

    /**
     * 세션 요약 조회 — 정답률, 시간, 문제별 결과.
     * 안 푼 문제의 정답은 NULL로 반환 (정답 노출 방지 — Pause/Resume도 자연스럽게 지원).
     */
    public SessionSummaryResponse getSummary(Long deckId, Long sessionId, Long userId) {
        // 1) 세션 검증
        QuizSession session = quizSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("세션을 찾을 수 없습니다"));
        if (!session.getUser().getId().equals(userId)) {
            throw new ForbiddenException("본인 세션이 아닙니다");
        }
        assertSessionDeck(session, deckId);

        // 2) 문제 N개 조회 (출제 순서대로)
        List<QuizQuestion> questions = quizQuestionRepository
                .findBySessionIdOrderByQuestionOrderAsc(sessionId);

        // 3) 통계 계산
        int answered = (int) questions.stream().filter(q -> q.getAnsweredAt() != null).count();
        int correct  = (int) questions.stream().filter(q -> Boolean.TRUE.equals(q.getIsCorrect())).count();
        int wrong    = answered - correct;
        long accuracy = answered > 0 ? Math.round((double) correct / answered * 100) : 0;

        // 4) 문제별 DTO 변환 (안 푼 문제는 correctAnswer=null → 정답 노출 방지)
        List<SessionSummaryResponse.QuestionResult> results = questions.stream()
                .map(q -> SessionSummaryResponse.QuestionResult.builder()
                        .questionId(q.getId())
                        .questionOrder(q.getQuestionOrder())
                        .question(q.getQuestionText())
                        .choices(fromJson(q.getChoicesJson()))
                        .correctAnswer(q.getAnsweredAt() != null ? q.getCorrectAnswer() : null)
                        .selectedAnswer(q.getSelectedAnswer())
                        .correct(q.getIsCorrect())
                        .build())
                .toList();

        // 5) 응답 빌드
        return SessionSummaryResponse.builder()
                .sessionId(session.getId())
                .direction(session.getDirection())
                .total(session.getTotal())
                .answered(answered)
                .correct(correct)
                .wrong(wrong)
                .accuracy(accuracy)
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .questions(results)
                .build();
    }

}
