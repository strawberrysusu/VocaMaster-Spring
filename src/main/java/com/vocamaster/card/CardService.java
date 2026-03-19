package com.vocamaster.card;

import com.vocamaster.card.dto.CardResponse;
import com.vocamaster.card.dto.CreateCardRequest;
import com.vocamaster.card.dto.UpdateCardRequest;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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
                .deck(deck)
                .build();

        return CardResponse.from(cardRepository.save(card));
    }

    public Page<CardResponse> findAll(Long deckId, Long userId, int page, int size, Boolean starredOnly) {
        deckService.verifyOwner(deckId, userId);
        PageRequest pageable = PageRequest.of(page, size);

        Page<Card> cards;
        if (Boolean.TRUE.equals(starredOnly)) {
            cards = cardRepository.findByDeckIdAndStarredTrue(deckId, pageable);
        } else {
            cards = cardRepository.findByDeckId(deckId, pageable);
        }
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

    private Card getCard(Long id) {
        return cardRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "카드를 찾을 수 없습니다"));
    }
}
