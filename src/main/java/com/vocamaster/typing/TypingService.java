package com.vocamaster.typing;

import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.common.Direction;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.common.exception.ForbiddenException;
import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckService;
import com.vocamaster.typing.dto.*;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TypingService {

    private static final int DEFAULT_TOTAL = 10;

    private final TypingSessionRepository typingSessionRepository;
    private final TypingQuestionRepository typingQuestionRepository;
    private final CardRepository cardRepository;
    private final DeckService deckService;
    private final UserRepository userRepository;

    /**
     * 세션 시작 — N문제 미리 생성 (ADR-026, Quiz Eager 패턴 재사용 + 선택지 없음).
     */
    @Transactional
    public StartTypingSessionResponse startSession(Long deckId, Long userId, StartTypingSessionRequest req) {
        Deck deck = deckService.verifyOwner(deckId, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다"));

        // 1) 카드 풀 결정 (전체 / 즐겨찾기만 — wrongOnly는 Typing엔 미적용)
        List<Card> pool;
        if (Boolean.TRUE.equals(req.getStarredOnly())) {
            pool = cardRepository.findByDeckIdAndStarredTrue(deckId);
        } else {
            pool = cardRepository.findByDeckId(deckId);
        }

        // 2) 최소 카드 수 검증 — Typing은 1장으로도 가능 (선택지 X)
        if (pool.isEmpty()) {
            throw new BadRequestException("타이핑할 카드가 없습니다");
        }

        Direction direction = Direction.from(req.getDirection());
        int requestedTotal = (req.getTotal() == null) ? DEFAULT_TOTAL : req.getTotal();
        int total = Math.min(requestedTotal, pool.size());          // 카드 부족하면 그만큼만

        // 3) 세션 row 저장
        TypingSession session = typingSessionRepository.save(TypingSession.builder()
                .user(user)
                .deck(deck)
                .direction(direction.name())
                .total(total)
                .build());

        // 4) 카드 셔플 → 출제할 N장 선정
        List<Card> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        List<Card> questionCards = shuffled.subList(0, total);

        // 5) 각 카드마다 문제 생성 → TypingQuestion N개 저장 (선택지 없음 — Quiz보다 단순)
        List<StartTypingSessionResponse.QuestionDto> dtos = new ArrayList<>();
        for (int i = 0; i < questionCards.size(); i++) {
            Card qc = questionCards.get(i);
            String questionText = direction.isFrontToBack() ? qc.getFront() : qc.getBack();
            String correctAnswer = direction.isFrontToBack() ? qc.getBack() : qc.getFront();

            TypingQuestion q = typingQuestionRepository.save(TypingQuestion.builder()
                    .session(session)
                    .card(qc)
                    .questionOrder(i)
                    .questionText(questionText)
                    .correctAnswer(correctAnswer)
                    .build());

            dtos.add(StartTypingSessionResponse.QuestionDto.builder()
                    .questionId(q.getId())
                    .questionOrder(i)
                    .question(questionText)
                    .build());
        }

        return StartTypingSessionResponse.builder()
                .sessionId(session.getId())
                .direction(direction.name())
                .total(total)
                .questions(dtos)
                .build();
    }

    /**
     * 세션 내 한 문제 답 제출. 채점 정책 = trim + ignoreCase + 쉼표 복수 정답 (ADR-026 결정 B).
     */
    @Transactional
    public SubmitTypedAnswerResponse submitTypedAnswer(Long sessionId, Long userId,
                                                       SubmitTypedAnswerRequest req) {
        // 1) 세션 검증 + 소유자 + 종료 여부
        TypingSession session = typingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("세션을 찾을 수 없습니다"));
        if (!session.getUser().getId().equals(userId)) {
            throw new ForbiddenException("본인 세션이 아닙니다");
        }
        if (session.getEndedAt() != null) {
            throw new BadRequestException("이미 종료된 세션입니다");
        }

        // 2) 문제 검증 (세션 소속 + 미제출)
        TypingQuestion question = typingQuestionRepository.findById(req.getQuestionId())
                .orElseThrow(() -> new NotFoundException("문제를 찾을 수 없습니다"));
        if (!question.getSession().getId().equals(sessionId)) {
            throw new BadRequestException("이 세션에 속하지 않는 문제입니다");
        }
        if (question.getAnsweredAt() != null) {
            throw new BadRequestException("이미 답한 문제입니다");
        }

        // 3) 채점 — 정규화 + 쉼표 복수 정답 매칭 (핵심)
        String typed = req.getTypedAnswer() == null ? "" : req.getTypedAnswer().trim();
        boolean isCorrect = isAnswerMatch(typed, question.getCorrectAnswer());

        // 4) UPDATE (dirty checking)
        question.setTypedAnswer(typed);
        question.setIsCorrect(isCorrect);
        question.setAnsweredAt(LocalDateTime.now());

        // 5) 모든 문제 풀었나? 마지막이면 세션 endedAt 채움
        List<TypingQuestion> all = typingQuestionRepository.findBySessionIdOrderByQuestionOrderAsc(sessionId);
        boolean sessionEnded = all.stream().allMatch(q -> q.getAnsweredAt() != null);
        if (sessionEnded) {
            session.setEndedAt(LocalDateTime.now());
        }

        return SubmitTypedAnswerResponse.builder()
                .correct(isCorrect)
                .correctAnswer(question.getCorrectAnswer())
                .typedAnswer(typed)
                .sessionEnded(sessionEnded)
                .build();
    }

    /**
     * 세션 요약 조회 — 안 푼 문제의 정답은 NULL (정답 노출 방지).
     */
    public TypingSessionSummaryResponse getSummary(Long sessionId, Long userId) {
        TypingSession session = typingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("세션을 찾을 수 없습니다"));
        if (!session.getUser().getId().equals(userId)) {
            throw new ForbiddenException("본인 세션이 아닙니다");
        }

        List<TypingQuestion> questions = typingQuestionRepository
                .findBySessionIdOrderByQuestionOrderAsc(sessionId);

        int answered = (int) questions.stream().filter(q -> q.getAnsweredAt() != null).count();
        int correct  = (int) questions.stream().filter(q -> Boolean.TRUE.equals(q.getIsCorrect())).count();
        int wrong    = answered - correct;
        long accuracy = answered > 0 ? Math.round((double) correct / answered * 100) : 0;

        List<TypingSessionSummaryResponse.QuestionResult> results = questions.stream()
                .map(q -> TypingSessionSummaryResponse.QuestionResult.builder()
                        .questionId(q.getId())
                        .questionOrder(q.getQuestionOrder())
                        .question(q.getQuestionText())
                        .correctAnswer(q.getAnsweredAt() != null ? q.getCorrectAnswer() : null)
                        .typedAnswer(q.getTypedAnswer())
                        .correct(q.getIsCorrect())
                        .build())
                .toList();

        return TypingSessionSummaryResponse.builder()
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

    /**
     * 쉼표 복수 정답 매칭 — ADR-026 결정 B.
     * "사과, 능금" → split → 각 trim → 사용자 입력과 하나라도 일치하면 정답.
     */
    private boolean isAnswerMatch(String typed, String correctAnswer) {
        if (typed.isBlank()) return false;
        for (String candidate : correctAnswer.split(",")) {
            if (typed.equalsIgnoreCase(candidate.trim())) {
                return true;
            }
        }
        return false;
    }
}
