package com.vocamaster.deck;

import com.vocamaster.card.CardRepository;
import com.vocamaster.common.exception.ForbiddenException;
import com.vocamaster.deck.dto.CreateDeckRequest;
import com.vocamaster.deck.dto.DeckResponse;
import com.vocamaster.deck.dto.UpdateDeckRequest;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeckService {

    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final UserRepository userRepository;

    public DeckResponse create(Long userId, CreateDeckRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Deck deck = Deck.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .user(user)
                .build();

        deckRepository.save(deck);
        return DeckResponse.listOf(deck, 0);
    }

    public List<DeckResponse> findAll(Long userId) {
        List<Deck> decks = deckRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return decks.stream()
                .map(d -> DeckResponse.listOf(d, cardRepository.countByDeckId(d.getId())))
                .toList();
    }

    public DeckResponse findOne(Long id, Long userId) {
        Deck deck = verifyOwner(id, userId);
        long cardCount = cardRepository.countByDeckId(id);
        long starredCount = cardRepository.countByDeckIdAndStarredTrue(id);
        return DeckResponse.from(deck, cardCount, starredCount);
    }

    public DeckResponse update(Long id, Long userId, UpdateDeckRequest req) {
        Deck deck = verifyOwner(id, userId);
        if (req.getTitle() != null) deck.setTitle(req.getTitle());
        if (req.getDescription() != null) deck.setDescription(req.getDescription());
        deckRepository.save(deck);
        return DeckResponse.listOf(deck, cardRepository.countByDeckId(id));
    }

    public void remove(Long id, Long userId) {
        verifyOwner(id, userId);
        deckRepository.deleteById(id);
    }

    // 소유권 확인 — 다른 서비스에서도 사용
    public Deck verifyOwner(Long deckId, Long userId) {
        Deck deck = deckRepository.findById(deckId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "단어장을 찾을 수 없습니다"));
        if (!deck.getUser().getId().equals(userId)) {
            throw new ForbiddenException("접근 권한이 없습니다");
        }
        return deck;
    }
}
