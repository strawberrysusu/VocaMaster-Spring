package com.vocamaster.deck;

import com.vocamaster.card.CardRepository;
import com.vocamaster.card.dto.PublicCardResponse;
import com.vocamaster.common.CurrentUser;
import com.vocamaster.common.PageableUtils;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.deck.dto.PublicDeckResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)   // 조회 전용 + LAZY(user) 접근이 트랜잭션 안에서 끝나도록 (OSIV 의존 X)
public class PublicDeckService {

    // '없는 덱'과 '비공개 덱'은 메시지까지 동일해야 함 — 다르면 그 차이로 존재가 샘 (ADR-030)
    // package-private: DeckService.copy의 404도 같은 메시지를 써야 함 (드리프트 방지)
    static final String DECK_NOT_FOUND = "단어장을 찾을 수 없습니다";

    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final DeckLikeRepository deckLikeRepository;
    private final DeckRankingService rankingService;

    // 응답 조립 — 로그인 사용자면 likedByMe(IN 한 방)·mine 계산, 익명이면 둘 다 false
    private List<PublicDeckResponse> toResponses(List<Deck> decks) {
        Long me = CurrentUser.tryGetId();
        Set<Long> liked = (me == null || decks.isEmpty())
                ? Set.of()
                : deckLikeRepository.findLikedDeckIds(me, decks.stream().map(Deck::getId).toList());
        return decks.stream()
                .map(d -> PublicDeckResponse.from(d, cardRepository.countByDeckId(d.getId()),
                        liked.contains(d.getId()),
                        me != null && me.equals(d.getUser().getId())))
                .toList();
    }

    public Page<PublicDeckResponse> search(String keyword, int page, int size, String sort) {
        String normalized = (keyword == null || keyword.isBlank()) ? null : keyword;
        var pageable = PageableUtils.safe(page, size, Sort.unsorted());   // 정렬은 JPQL order by가 담당

        // 캐시는 좁고 뜨거운 질문 하나만: sort=popular + 검색어 없음 (ZSET은 제목을 모름, ADR-035)
        if ("popular".equals(sort) && normalized == null) {
            Page<PublicDeckResponse> cached = searchPopularFromCache(pageable);
            if (cached != null) return cached;                            // null이면 아래 DB 경로로
        }

        Page<Deck> decks;
        if (sort == null || sort.isBlank() || sort.equals("recent")) {
            decks = deckRepository.searchByVisibility(DeckVisibility.PUBLIC, normalized, pageable);
        } else if (sort.equals("popular")) {
            decks = deckRepository.searchByVisibilityPopular(DeckVisibility.PUBLIC, normalized, pageable);
        } else {
            throw new BadRequestException("sort는 recent 또는 popular만 가능합니다");
        }
        return new PageImpl<>(toResponses(decks.getContent()), pageable, decks.getTotalElements());
    }

    /**
     * 랭킹 캐시 경로. null 반환 = 이번 요청은 DB 경로로 (장애·미가동·stale 불일치 전부).
     * 캐시는 id/순서만 — 내용과 PUBLIC 재검증은 DB가 한다 (비공개 노출 구조적 차단).
     */
    private Page<PublicDeckResponse> searchPopularFromCache(PageRequest pageable) {
        List<Long> ids = rankingService.topDeckIds(pageable.getPageNumber(), pageable.getPageSize());
        if (ids == null) return null;

        // ZCARD는 낡은 id를 셀 수 있어 총계는 DB가 정확 (비용은 count 하나 — 우리가 아끼려는 건 정렬이지 개수가 아님)
        long total = deckRepository.countByVisibility(DeckVisibility.PUBLIC);
        if (ids.isEmpty()) return new PageImpl<>(List.of(), pageable, total);

        List<Deck> decks = deckRepository.findByIdInAndVisibilityWithUser(ids, DeckVisibility.PUBLIC);
        if (decks.size() < ids.size()) {
            // 비공개 전환·삭제 직후의 낡은 id — 청소(자가 치유)하고 이번 요청은 DB로 (페이지 구멍 방지)
            Set<Long> alive = decks.stream().map(Deck::getId).collect(Collectors.toSet());
            rankingService.evictStale(ids.stream().filter(id -> !alive.contains(id)).toList());
            return null;
        }

        Map<Long, Deck> byId = decks.stream().collect(Collectors.toMap(Deck::getId, Function.identity()));
        List<Deck> ordered = ids.stream().map(byId::get).toList();   // ★ IN 조회는 순서 미보장 — Redis 순서로 재조립
        return new PageImpl<>(toResponses(ordered), pageable, total);
    }

    public PublicDeckResponse findOne(Long deckId) {
        return toResponses(List.of(visibleDeckOrThrow(deckId))).get(0);
    }

    // 공개 덱 카드 미리보기 — 소유자 전용 /decks/{id}/cards와 달리 PUBLIC/UNLISTED면 누구나.
    // 내용을 보기 전에 복사부터 해야 했던 UX 구멍 해소. 접근 규칙은 findOne과 동일(PRIVATE=404)
    public Page<PublicCardResponse> findCards(Long deckId, int page, int size) {
        visibleDeckOrThrow(deckId);
        var pageable = PageableUtils.safe(page, size, Sort.by("position").ascending().and(Sort.by("id")));
        return cardRepository.findByDeckId(deckId, pageable).map(PublicCardResponse::from);
    }

    private Deck visibleDeckOrThrow(Long deckId) {
        Deck deck = deckRepository.findById(deckId)
                .orElseThrow(() -> new NotFoundException(DECK_NOT_FOUND));
        if (deck.getVisibility() == DeckVisibility.PRIVATE) {
            throw new NotFoundException(DECK_NOT_FOUND);   // 403 아님 — 존재 자체를 숨김
        }
        if (deck.getUser().isDeleted()) {
            throw new NotFoundException(DECK_NOT_FOUND);   // 탈퇴 사용자의 콘텐츠 — 목록과 동일하게 숨김 (Codex 검산 8/28)
        }
        return deck;
    }
}
