package com.vocamaster.quiz;

import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.card.dto.CardResponse;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.deck.DeckService;
import com.vocamaster.quiz.dto.*;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;


import com.vocamaster.common.Direction;

import java.util.*;

@Service
@RequiredArgsConstructor
public class QuizService {

    private final QuizAttemptRepository quizAttemptRepository;
    private final CardRepository cardRepository;
    private final DeckService deckService;
    private final UserRepository userRepository;

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
}
