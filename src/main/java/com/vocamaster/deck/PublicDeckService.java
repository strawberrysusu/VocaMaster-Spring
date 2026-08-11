package com.vocamaster.deck;

import com.vocamaster.card.CardRepository;
import com.vocamaster.common.PageableUtils;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.deck.dto.PublicDeckResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)   // 조회 전용 + LAZY(user) 접근이 트랜잭션 안에서 끝나도록 (OSIV 의존 X)
public class PublicDeckService {

    // '없는 덱'과 '비공개 덱'은 메시지까지 동일해야 함 — 다르면 그 차이로 존재가 샘 (ADR-030)
    // package-private: DeckService.copy의 404도 같은 메시지를 써야 함 (드리프트 방지)
    static final String DECK_NOT_FOUND = "단어장을 찾을 수 없습니다";

    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;

    public Page<PublicDeckResponse> search(String keyword, int page, int size, String sort) {
        String normalized = (keyword == null || keyword.isBlank()) ? null : keyword;
        var pageable = PageableUtils.safe(page, size, Sort.unsorted());   // 정렬은 JPQL order by가 담당

        Page<Deck> decks;
        if (sort == null || sort.isBlank() || sort.equals("recent")) {
            decks = deckRepository.searchByVisibility(DeckVisibility.PUBLIC, normalized, pageable);
        } else if (sort.equals("popular")) {
            decks = deckRepository.searchByVisibilityPopular(DeckVisibility.PUBLIC, normalized, pageable);
        } else {
            throw new BadRequestException("sort는 recent 또는 popular만 가능합니다");
        }
        return decks.map(d -> PublicDeckResponse.from(d, cardRepository.countByDeckId(d.getId())));
    }

    public PublicDeckResponse findOne(Long deckId) {
        Deck deck = deckRepository.findById(deckId)
                .orElseThrow(() -> new NotFoundException(DECK_NOT_FOUND));
        if (deck.getVisibility() == DeckVisibility.PRIVATE) {
            throw new NotFoundException(DECK_NOT_FOUND);   // 403 아님 — 존재 자체를 숨김
        }
        return PublicDeckResponse.from(deck, cardRepository.countByDeckId(deckId));
    }
}
