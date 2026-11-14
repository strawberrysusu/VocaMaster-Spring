package com.vocamaster.card;

import com.vocamaster.card.dto.CardResponse;
import com.vocamaster.card.dto.CreateCardRequest;
import com.vocamaster.card.dto.SetStarredRequest;
import com.vocamaster.card.dto.SetStarredResponse;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
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
     * 토글이 아니라 값 지정이라 두 번 보내도 같다.
     * <p>이 덱의 카드만 바꾼다. 이 덱에 없는 id(시험 도중 지워진 카드, 다른 덱의 카드)는 건너뛰고 skippedIds로 알려 준다 —
     * 예전엔 하나라도 섞이면 400이라, 시험 중 카드 한 장만 지워져도 그 시험의 별표가 전부 실패했다 (11/14 검토).
     * 지워진 카드와 다른 덱 카드를 똑같이 건너뛰므로 응답만 보고 남의 카드 id가 있는지 알 수는 없다.
     * <p>엔티티로 바꾸는 이유: 벌크 UPDATE는 @UpdateTimestamp를 건너뛰어 updated_at이 안 바뀌고, 값이 실제로 바뀐 카드만 골라 UPDATE 하기도 어렵다.
     */
    @Transactional
    public SetStarredResponse setStarred(Long deckId, Long userId, SetStarredRequest req) {
        deckService.verifyOwner(deckId, userId);
        Set<Long> ids = new LinkedHashSet<>(req.getCardIds());
        boolean target = req.getStarred();

        List<Long> changed = new ArrayList<>();
        Set<Long> found = new HashSet<>();
        for (Card card : cardRepository.findByDeckIdAndIdIn(deckId, ids)) {   // 이 덱에 있는 카드만 온다
            found.add(card.getId());
            if (!Boolean.valueOf(target).equals(card.getStarred())) {   // 옛 데이터의 null은 '꺼짐'으로 본다
                card.setStarred(target);
                changed.add(card.getId());
            }
        }
        List<Long> skipped = new ArrayList<>(ids);
        skipped.removeAll(found);   // 보낸 id 중 이 덱에서 못 찾은 것

        changed.sort(null);
        skipped.sort(null);
        return new SetStarredResponse(target, changed, skipped);
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
