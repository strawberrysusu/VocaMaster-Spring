package com.vocamaster.card;

import com.vocamaster.card.dto.CardResponse;
import com.vocamaster.card.dto.CreateCardRequest;
import com.vocamaster.card.dto.SetStarredRequest;
import com.vocamaster.card.dto.SetStarredResponse;
import com.vocamaster.card.dto.UpdateCardRequest;
import com.vocamaster.common.PageableUtils;
import com.vocamaster.common.exception.BadRequestException;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
                .memo(blankToNull(req.getMemo()))
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
        if (req.getMemo() != null) card.setMemo(blankToNull(req.getMemo()));   // ""로 보내면 설명 삭제 (읽기와 같은 규칙)
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

    /**
     * 별표 일괄 설정 — 온라인 시험지에서 틀린 카드 자동 ⭐, '맞음으로' 되돌리기 (ADR-053).
     * 토글이 아니라 값 지정이라 두 번 보내도 같다. 이 덱에 없는 카드가 하나라도 섞이면 아무것도 바꾸지 않는다.
     * 엔티티로 바꾸는 이유: 벌크 UPDATE는 @UpdateTimestamp(JVM KST)를 건너뛰고, DB NOW()는 컨테이너 UTC라 시간대가 어긋난다.
     */
    @Transactional
    public SetStarredResponse setStarred(Long deckId, Long userId, SetStarredRequest req) {
        deckService.verifyOwner(deckId, userId);
        Set<Long> ids = new LinkedHashSet<>(req.getCardIds());
        List<Card> cards = cardRepository.findAllById(ids);
        boolean allInDeck = cards.size() == ids.size()
                && cards.stream().allMatch(c -> c.getDeck().getId().equals(deckId));
        if (!allInDeck) {
            throw new BadRequestException("이 단어장에 없는 카드가 섞여 있습니다");
        }
        boolean target = req.getStarred();
        List<Long> changed = new ArrayList<>();
        for (Card card : cards) {
            if (!Boolean.valueOf(target).equals(card.getStarred())) {   // 옛 데이터의 null은 '꺼짐'으로 본다
                card.setStarred(target);
                changed.add(card.getId());
            }
        }
        changed.sort(null);
        return new SetStarredResponse(target, changed);
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
