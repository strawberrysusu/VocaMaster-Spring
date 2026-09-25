package com.vocamaster.card;

import com.vocamaster.card.dto.CardResponse;
import com.vocamaster.card.dto.CreateCardRequest;
import com.vocamaster.card.dto.UpdateCardRequest;
import com.vocamaster.common.PageableUtils;
import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckService;
import com.vocamaster.review.CardProgress;
import com.vocamaster.review.CardProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CardService {

    private final CardRepository cardRepository;
    private final DeckService deckService;
    private final CardProgressRepository cardProgressRepository;

    public CardResponse create(Long deckId, Long userId, CreateCardRequest req) {
        Deck deck = deckService.verifyOwner(deckId, userId);

        Card card = Card.builder()
                .front(req.getFront())
                .back(req.getBack())
                .reading(blankToNull(req.getReading()))
                .exampleSentence(req.getExampleSentence())
                .memo(req.getMemo())
                .position(req.getPosition())
                .deck(deck)
                .build();

        return CardResponse.from(cardRepository.save(card));
    }

    public Page<CardResponse> findAll(Long deckId, Long userId, int page, int size, String keyword, Boolean starredOnly
    , String sort) {
        deckService.verifyOwner(deckId, userId);
        Sort sortOrder = resolveSort(sort);
        PageRequest pageable = PageableUtils.safe(page, size, sortOrder);
        String safeKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        Page<Card> cards = cardRepository.search(deckId, safeKeyword, starredOnly, pageable);
        if (cards.isEmpty()) {
            return cards.map(CardResponse::from);
        }
        List<Long> cardIds = cards.getContent().stream().map(Card::getId).toList();
        Map<Long, CardProgress> progressByCard = cardProgressRepository
                .findByUserIdAndCardIdIn(userId, cardIds).stream()
                .collect(Collectors.toMap(progress -> progress.getCard().getId(), Function.identity()));
        return cards.map(card -> CardResponse.from(card, progressByCard.get(card.getId())));
    }

    public CardResponse findOne(Long id, Long userId) {
        Card card = getCard(id);
        deckService.verifyOwner(card.getDeck().getId(), userId);
        return withProgress(card, userId);
    }

    public CardResponse update(Long id, Long userId, UpdateCardRequest req) {
        Card card = getCard(id);
        deckService.verifyOwner(card.getDeck().getId(), userId);

        if (req.getFront() != null) card.setFront(req.getFront());
        if (req.getBack() != null) card.setBack(req.getBack());
        if (req.getReading() != null) card.setReading(blankToNull(req.getReading()));   // ""로 보내면 읽기 삭제
        if (req.getExampleSentence() != null) card.setExampleSentence(req.getExampleSentence());
        if (req.getMemo() != null) card.setMemo(req.getMemo());
        if (req.getPosition() != null) card.setPosition(req.getPosition());
        return withProgress(cardRepository.save(card), userId);
    }

    public void remove(Long id, Long userId) {
        Card card = getCard(id);
        deckService.verifyOwner(card.getDeck().getId(), userId);
        cardRepository.delete(card);
    }

    public CardResponse toggleStar(Long id, Long userId) {
        Card card = getCard(id);
        deckService.verifyOwner(card.getDeck().getId(), userId);
        card.setStarred(!card.getStarred());
        return withProgress(cardRepository.save(card), userId);
    }

    private CardResponse withProgress(Card card, Long userId) {
        return CardResponse.from(card,
                cardProgressRepository.findByUserIdAndCardId(userId, card.getId()).orElse(null));
    }
    private Sort resolveSort(String sort) {
        if (sort == null) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        return switch (sort) {
            case "position" -> Sort.by(Sort.Order.asc("position").nullsLast());
            case "starred" -> Sort.by(Sort.Direction.DESC, "starred", "createdAt");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    private Card getCard(Long id) {
        return cardRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("카드를 찾을 수 없습니다"));
    }

    // 읽기 칸은 선택 — 빈 문자열은 null로 (영어 덱에 ""가 쌓이지 않게, 표시 조건도 null 하나로)
    static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
