package com.vocamaster.card;

import com.vocamaster.card.dto.CardResponse;
import com.vocamaster.card.dto.CreateCardRequest;
import com.vocamaster.card.dto.UpdateCardRequest;
import com.vocamaster.common.PageableUtils;
import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CardService {

    private final CardRepository cardRepository;
    private final DeckService deckService;

    public CardResponse create(Long deckId, Long userId, CreateCardRequest req) {
        Deck deck = deckService.verifyOwner(deckId, userId);

        Card card = Card.builder()
                .front(req.getFront())
                .back(req.getBack())
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
        return cards.map(CardResponse::from);

    }

    public CardResponse findOne(Long id, Long userId) {
        Card card = getCard(id);
        deckService.verifyOwner(card.getDeck().getId(), userId);
        return CardResponse.from(card);
    }

    public CardResponse update(Long id, Long userId, UpdateCardRequest req) {
        Card card = getCard(id);
        deckService.verifyOwner(card.getDeck().getId(), userId);

        if (req.getFront() != null) card.setFront(req.getFront());
        if (req.getBack() != null) card.setBack(req.getBack());
        if (req.getExampleSentence() != null) card.setExampleSentence(req.getExampleSentence());
        if (req.getMemo() != null) card.setMemo(req.getMemo());
        if (req.getPosition() != null) card.setPosition(req.getPosition());
        return CardResponse.from(cardRepository.save(card));
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
        return CardResponse.from(cardRepository.save(card));
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
}
