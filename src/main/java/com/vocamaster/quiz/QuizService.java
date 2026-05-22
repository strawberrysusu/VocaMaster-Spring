package com.vocamaster.quiz;

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
    private static final int MAX_CHOICES = 4;

    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizSessionRepository quizSessionRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final CardRepository cardRepository;
    private final DeckService deckService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

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

        List<Card> wrongCards = shuffled.subList(1, Math.min(5, shuffled.size()));

        String question = direction.isFrontToBack() ? questionCard.getFront() : questionCard.getBack();
        String correctAnswer = direction.isFrontToBack() ? questionCard.getBack() : questionCard.getFront();

        List<String> choices = new ArrayList<>();
        choices.add(correctAnswer);
        for (Card c : wrongCards) {
            choices.add(direction.isFrontToBack() ? c.getBack() : c.getFront());
        }
        Collections.shuffle(choices);

        return QuizQuestionResponse.builder()
                .cardId(questionCard.getId())
                .question(question)
                .choices(choices)
                .direction(req.getDirection())
                .build();
    }

    // 퀴즈 답안 제출 — 서버에서 정답 판정
    public QuizResultResponse submitAnswer(Long deckId, Long userId, SubmitQuizRequest req) {
        deckService.verifyOwner(deckId, userId);

        Card card = cardRepository.findById(req.getCardId())
                .orElseThrow(() -> new NotFoundException("카드를 찾을 수 없습니다"));

        if (!card.getDeck().getId().equals(deckId)) {
            throw new BadRequestException("이 덱에 속하지 않는 카드입니다");
        }

        Direction dir = Direction.from(req.getDirection());
        String correctAnswer = dir.isFrontToBack() ? card.getBack() : card.getFront();
        boolean isCorrect = req.getSelectedAnswer().trim().equals(correctAnswer.trim());

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

        // 1) 카드 풀 결정 (전체 / 오답만 / 즐겨찾기만)
        List<Card> pool;
        if (Boolean.TRUE.equals(req.getWrongOnly())) {
            List<Long> wrongIds = quizAttemptRepository.findWrongCardIds(deckId, userId);
            if (wrongIds.isEmpty()) throw new BadRequestException("오답 카드가 없습니다");
            pool = cardRepository.findAllById(wrongIds).stream()
                    .filter(c -> c.getDeck().getId().equals(deckId))
                    .toList();
        } else if (Boolean.TRUE.equals(req.getStarredOnly())) {
            pool = cardRepository.findByDeckIdAndStarredTrue(deckId);
        } else {
            pool = cardRepository.findByDeckId(deckId);
        }

        // 2) 최소 카드 수 검증 (오답지 만들려면 2개 이상)
        if (pool.size() < 2) {
            throw new BadRequestException(
                    "퀴즈에 사용 가능한 카드가 최소 2개 필요합니다. 현재 " + pool.size() + "개");
        }

        Direction direction = Direction.from(req.getDirection());
        int requestedTotal = (req.getTotal() == null) ? DEFAULT_TOTAL : req.getTotal();
        int total = Math.min(requestedTotal, pool.size());          // 카드 부족하면 그만큼만
        int choiceCount = Math.min(MAX_CHOICES, pool.size());       // 5개 미만이면 2~4지선다 fallback

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
            String correctAnswer = direction.isFrontToBack() ? qc.getBack() : qc.getFront();

            List<String> choices = buildChoices(pool, qc, correctAnswer, direction, choiceCount);

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
    private List<String> buildChoices(List<Card> pool, Card questionCard, String correctAnswer,
                                       Direction direction, int choiceCount) {
        List<Card> wrongPool = new ArrayList<>(pool);
        wrongPool.remove(questionCard);
        Collections.shuffle(wrongPool);

        Set<String> seen = new HashSet<>();
        seen.add(correctAnswer);
        List<String> choices = new ArrayList<>();
        choices.add(correctAnswer);
        for (Card w : wrongPool) {
            String wrongAns = direction.isFrontToBack() ? w.getBack() : w.getFront();
            if (seen.add(wrongAns)) {                               // 같은 답 중복 차단
                choices.add(wrongAns);
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
    public SubmitToSessionResponse submitAnswerToSession(Long sessionId, Long userId,
                                                          SubmitToSessionRequest req) {
        // 1) 세션 검증 + 소유자 확인 + 종료 여부
        QuizSession session = quizSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("세션을 찾을 수 없습니다"));
        if (!session.getUser().getId().equals(userId)) {
            throw new ForbiddenException("본인 세션이 아닙니다");
        }
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
        String normalized = req.getSelectedAnswer() == null ? "" : req.getSelectedAnswer().trim();
        boolean isCorrect = normalized.equalsIgnoreCase(question.getCorrectAnswer().trim());

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
    public SessionSummaryResponse getSummary(Long sessionId, Long userId) {
        // 1) 세션 검증
        QuizSession session = quizSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("세션을 찾을 수 없습니다"));
        if (!session.getUser().getId().equals(userId)) {
            throw new ForbiddenException("본인 세션이 아닙니다");
        }

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
