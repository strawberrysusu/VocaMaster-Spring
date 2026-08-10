package com.vocamaster.deck;

import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.deck.dto.LikeResponse;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeckLikeService {

    private final DeckRepository deckRepository;
    private final DeckLikeRepository deckLikeRepository;
    private final UserRepository userRepository;

    @Transactional
    public LikeResponse like(Long deckId, Long userId) {
        Deck deck = visibleDeckOrThrow(deckId, userId);
        if (deckLikeRepository.existsByUserIdAndDeckId(userId, deckId)) {
            return LikeResponse.of(true, deck.getLikeCount());   // 빠른 경로 — 멱등 (ADR-032)
        }

        // ★ 잠금 순서: 카운트 X락 먼저 — DeckLike INSERT의 FK S락과의 교착 방지 (ADR-031 교훈 재적용)
        //   더블탭 레이스로 아래 INSERT가 unique 위반이 나면 이 증가까지 통째로 롤백 → 카운트 정확성 유지
        deckRepository.incrementLikeCount(deckId);

        Deck fresh = deckRepository.findById(deckId)
                .orElseThrow(() -> new NotFoundException(PublicDeckService.DECK_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을수없습니다."));
        deckLikeRepository.save(DeckLike.builder().user(user).deck(fresh).build());

        return LikeResponse.of(true, fresh.getLikeCount());
    }

    @Transactional
    public LikeResponse unlike(Long deckId, Long userId) {
        Deck deck = visibleDeckOrThrow(deckId, userId);

        long deleted = deckLikeRepository.deleteByUserIdAndDeckId(userId, deckId);
        if (deleted > 0) {                                        // 지웠을 때만 -1 → 자연 멱등, 음수 불가
            deckRepository.decrementLikeCount(deckId);
            return LikeResponse.of(false, deckRepository.findById(deckId)
                    .orElseThrow(() -> new NotFoundException(PublicDeckService.DECK_NOT_FOUND))
                    .getLikeCount());
        }
        return LikeResponse.of(false, deck.getLikeCount());
    }

    // 더블탭 레이스가 unique 위반으로 롤백된 뒤, 컨트롤러가 현재 상태를 멱등 응답으로 재조회할 때 사용
    @Transactional(readOnly = true)
    public LikeResponse currentState(Long deckId, Long userId) {
        Deck deck = visibleDeckOrThrow(deckId, userId);
        boolean liked = deckLikeRepository.existsByUserIdAndDeckId(userId, deckId);
        return LikeResponse.of(liked, deck.getLikeCount());
    }

    // 접근 규칙은 복사(ADR-031)와 동일: 남의 PRIVATE = 없는 덱과 같은 404, 자기 덱은 visibility 무관
    private Deck visibleDeckOrThrow(Long deckId, Long userId) {
        Deck deck = deckRepository.findById(deckId)
                .orElseThrow(() -> new NotFoundException(PublicDeckService.DECK_NOT_FOUND));
        if (!deck.getUser().getId().equals(userId)
                && deck.getVisibility() == DeckVisibility.PRIVATE) {
            throw new NotFoundException(PublicDeckService.DECK_NOT_FOUND);
        }
        return deck;
    }
}
