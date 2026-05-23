package com.vocamaster.wrongnote;

import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.card.dto.CardResponse;
import com.vocamaster.deck.DeckService;
import com.vocamaster.quiz.QuizAttemptRepository;
import com.vocamaster.study.StudyRecordRepository;
import com.vocamaster.typing.TypingQuestionRepository;
import com.vocamaster.wrongnote.dto.WrongNoteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 통합 오답노트 (ADR-028) — Aggregator 패턴.
 *
 * Quiz / Typing / Flashcard 각 Repository를 호출해서 *카드 ID*를 수집한 뒤
 * 중복 제거 + 한 번에 카드 정보 조회 + 모드별/통합 응답 빌드.
 */
@Service
@RequiredArgsConstructor
public class WrongNoteService {

    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final QuizAttemptRepository quizAttemptRepository;
    private final TypingQuestionRepository typingQuestionRepository;
    private final StudyRecordRepository studyRecordRepository;
    private final CardRepository cardRepository;
    private final DeckService deckService;

    public WrongNoteResponse getWrongNotes(Long deckId, Long userId, int days) {
        // 1) 덱 소유자 검증
        deckService.verifyOwner(deckId, userId);

        // 2) since 계산 (days=0이면 전체 = EPOCH)
        LocalDateTime since = days > 0 ? LocalDateTime.now().minusDays(days) : EPOCH;

        // 3) 3 모드 오답 카드 ID 수집 (각 Repository JPQL이 deckId/userId 검증)
        List<Long> quizIds      = quizAttemptRepository.findWrongCardIdsSince(deckId, userId, since);
        List<Long> typingIds    = typingQuestionRepository.findWrongCardIdsByDeckIdAndUserIdSince(deckId, userId, since);
        List<Long> flashcardIds = studyRecordRepository.findUnknownCardIdsByDeckIdAndUserIdSince(deckId, userId, since);

        // 4) 통합 (LinkedHashSet — 순서 보존, dedup 자동)
        Set<Long> combinedIds = new LinkedHashSet<>();
        combinedIds.addAll(quizIds);
        combinedIds.addAll(typingIds);
        combinedIds.addAll(flashcardIds);

        // 5) 카드 정보 *한 번*만 조회 → ID → CardResponse Map (덱 외부 카드는 안전 필터)
        Map<Long, CardResponse> cardMap = cardRepository.findAllById(combinedIds).stream()
                .filter(c -> c.getDeck().getId().equals(deckId))
                .collect(Collectors.toMap(Card::getId, CardResponse::from));

        // 모드별 응답 빌드 (cardMap lookup)
        List<CardResponse> quizCards      = toCards(quizIds, cardMap);
        List<CardResponse> typingCards    = toCards(typingIds, cardMap);
        List<CardResponse> flashcardCards = toCards(flashcardIds, cardMap);
        List<CardResponse> combinedCards  = toCards(combinedIds, cardMap);

        return WrongNoteResponse.builder()
                .quiz(quizCards)
                .typing(typingCards)
                .flashcard(flashcardCards)
                .combined(combinedCards)
                .total(combinedCards.size())
                .days(days)
                .build();
    }

    /** ID 컬렉션 → CardResponse 리스트 (cardMap에 없는 ID는 생략 — 삭제된 카드 안전) */
    private List<CardResponse> toCards(Collection<Long> ids, Map<Long, CardResponse> cardMap) {
        return ids.stream()
                .map(cardMap::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
