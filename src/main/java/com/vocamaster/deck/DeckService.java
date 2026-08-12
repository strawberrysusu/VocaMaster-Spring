package com.vocamaster.deck;

import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.common.exception.ForbiddenException;
import com.vocamaster.deck.dto.CreateDeckRequest;
import com.vocamaster.deck.dto.DeckResponse;
import com.vocamaster.deck.dto.UpdateDeckRequest;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.vocamaster.common.exception.NotFoundException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeckService {

    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final DeckRankingService rankingService;

    public DeckResponse create(Long userId, CreateDeckRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을수없습니다."));

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

    // @Transactional: afterCommit 랭킹 훅의 등록 지점이 되려면 실제 트랜잭션 경계가 필요 (ADR-035)
    @Transactional
    public DeckResponse updateVisibility(Long id, Long userId, DeckVisibility visibility) {
        Deck deck = verifyOwner(id, userId);
        DeckVisibility before = deck.getVisibility();
        deck.setVisibility(visibility);
        deckRepository.save(deck);

        if (before == DeckVisibility.PUBLIC && visibility != DeckVisibility.PUBLIC) {
            rankingService.onLeftPublic(id);        // 노출은 DB 필터가 막지만, 순위표 품질 유지를 위해 즉시 제거
        } else if (before != DeckVisibility.PUBLIC && visibility == DeckVisibility.PUBLIC) {
            rankingService.onBecamePublic(id, deck.getLikeCount(), deck.getCopyCount());
        }
        return DeckResponse.listOf(deck, cardRepository.countByDeckId(id));
    }

    @Transactional
    public void remove(Long id, Long userId) {
        verifyOwner(id, userId);
        deckRepository.deleteById(id);
        rankingService.onLeftPublic(id);            // 비공개 덱이었어도 없는 멤버 ZREM은 무해
    }

    // 복사 (ADR-031): 완전한 복사본 또는 아무것도 없음 — 전체가 한 트랜잭션
    @Transactional
    public DeckResponse copy(Long deckId, Long userId) {
        Deck original = deckRepository.findById(deckId)
                .orElseThrow(() -> new NotFoundException(PublicDeckService.DECK_NOT_FOUND));

        boolean isOwner = original.getUser().getId().equals(userId);
        if (!isOwner && original.getVisibility() == DeckVisibility.PRIVATE) {
            // 남의 비공개 = 없는 덱과 동일 응답 (존재 숨김). 자기 덱은 visibility 무관 복사 가능
            throw new NotFoundException(PublicDeckService.DECK_NOT_FOUND);
        }

        // ★ 잠금 순서: 원본 행의 X 잠금(카운트 UPDATE)을 복사본 INSERT보다 먼저.
        //   복사본 INSERT는 original_deck_id FK 검사로 원본 행에 S 잠금을 걸어서,
        //   S(A)+S(B) 뒤에 서로 X로 승급하려는 순간 교착 — 동시성 테스트가 실제로 잡아낸 데드락 (ADR-031)
        //   실패 시에도 같은 트랜잭션이라 카운트만 오르는 일 없음 (전체 롤백)
        if (!isOwner) {
            deckRepository.incrementCopyCount(deckId);      // 원자적 +1. 자기 복사는 카운트 제외 — 인기 조작 방지
            rankingService.onCopied(deckId);                // 커밋 확정 후 +3 (실패·롤백 시 실행 안 됨)
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을수없습니다."));

        Deck copy = deckRepository.save(Deck.builder()
                .title(original.getTitle())
                .description(original.getDescription())
                .user(user)
                .originalDeckId(original.getId())
                .build());                                  // visibility는 @Builder.Default → PRIVATE

        List<Card> copiedCards = cardRepository.findByDeckId(deckId).stream()
                .map(c -> Card.builder()
                        .front(c.getFront())
                        .back(c.getBack())
                        .exampleSentence(c.getExampleSentence())
                        .memo(c.getMemo())                  // memo는 공유 콘텐츠로 분류 (ADR-031)
                        .position(c.getPosition())
                        .deck(copy)                         // starred는 @Builder.Default → false (학습 상태 리셋)
                        .build())
                .toList();
        cardRepository.saveAll(copiedCards);

        return DeckResponse.listOf(copy, copiedCards.size());
    }

    // 소유권 확인 — 다른 서비스에서도 사용
    public Deck verifyOwner(Long deckId, Long userId) {
        Deck deck = deckRepository.findById(deckId)
                .orElseThrow(() -> new NotFoundException("단어장을 찾을 수 없습니다"));
        if (!deck.getUser().getId().equals(userId)) {
            throw new ForbiddenException("접근 권한이 없습니다");
        }
        return deck;
    }
}
