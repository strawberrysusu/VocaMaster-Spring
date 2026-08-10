package com.vocamaster.deck;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.deck.dto.LikeResponse;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class DeckLikeServiceTest extends AbstractIntegrationTest {

    @Autowired private DeckLikeService deckLikeService;
    @Autowired private DeckLikeRepository deckLikeRepository;
    @Autowired private DeckService deckService;
    @Autowired private DeckRepository deckRepository;
    @Autowired private UserRepository userRepository;

    private User owner;
    private User liker;
    private Deck publicDeck;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(User.builder()
                .email("like-owner@test.com").password("encoded").nickname("주인").build());
        liker = userRepository.save(User.builder()
                .email("liker@test.com").password("encoded").nickname("팬").build());
        publicDeck = deckRepository.save(Deck.builder()
                .title("좋아요 대상").visibility(DeckVisibility.PUBLIC).user(owner).build());
    }

    @Test
    @DisplayName("좋아요 성공 — liked true, 카운트 1, 행 생성")
    void like_success() {
        LikeResponse res = deckLikeService.like(publicDeck.getId(), liker.getId());

        assertTrue(res.isLiked());
        assertEquals(1, res.getLikeCount());
        assertTrue(deckLikeRepository.existsByUserIdAndDeckId(liker.getId(), publicDeck.getId()));
        assertEquals(1, deckRepository.findById(publicDeck.getId()).orElseThrow().getLikeCount());
    }

    @Test
    @DisplayName("좋아요 중복 — 몇 번을 눌러도 카운트 1 (멱등)")
    void like_idempotent() {
        deckLikeService.like(publicDeck.getId(), liker.getId());
        LikeResponse second = deckLikeService.like(publicDeck.getId(), liker.getId());
        LikeResponse third = deckLikeService.like(publicDeck.getId(), liker.getId());

        assertTrue(second.isLiked());
        assertEquals(1, second.getLikeCount());
        assertEquals(1, third.getLikeCount());
        assertEquals(1, deckRepository.findById(publicDeck.getId()).orElseThrow().getLikeCount());
    }

    @Test
    @DisplayName("좋아요 취소 — liked false, 카운트 0, 행 삭제")
    void unlike_success() {
        deckLikeService.like(publicDeck.getId(), liker.getId());

        LikeResponse res = deckLikeService.unlike(publicDeck.getId(), liker.getId());

        assertFalse(res.isLiked());
        assertEquals(0, res.getLikeCount());
        assertFalse(deckLikeRepository.existsByUserIdAndDeckId(liker.getId(), publicDeck.getId()));
    }

    @Test
    @DisplayName("안 누른 좋아요 취소 — 에러 없이 카운트 그대로 (멱등)")
    void unlike_withoutLike_idempotent() {
        LikeResponse res = deckLikeService.unlike(publicDeck.getId(), liker.getId());

        assertFalse(res.isLiked());
        assertEquals(0, res.getLikeCount());
        assertEquals(0, deckRepository.findById(publicDeck.getId()).orElseThrow().getLikeCount());
    }

    @Test
    @DisplayName("남의 PRIVATE 덱 좋아요는 404 — 존재 숨김 (복사와 동일 규칙)")
    void like_othersPrivate_404() {
        Deck hidden = deckRepository.save(Deck.builder()
                .title("비공개").visibility(DeckVisibility.PRIVATE).user(owner).build());

        assertThrows(NotFoundException.class, () ->
                deckLikeService.like(hidden.getId(), liker.getId()));
    }

    @Test
    @DisplayName("자기 덱 좋아요 허용 — unique 제약이 1회 상한이라 조작 여지 캡 (ADR-032)")
    void like_ownDeck_allowed() {
        LikeResponse res = deckLikeService.like(publicDeck.getId(), owner.getId());

        assertTrue(res.isLiked());
        assertEquals(1, res.getLikeCount());

        // 자기 좋아요도 중복은 불가 — 여기가 '상한 1회'의 실체
        assertEquals(1, deckLikeService.like(publicDeck.getId(), owner.getId()).getLikeCount());
    }

    @Test
    @DisplayName("UNLISTED도 좋아요 가능 — 볼 수 있으면 반응 가능")
    void like_unlisted_ok() {
        Deck linked = deckRepository.save(Deck.builder()
                .title("링크덱").visibility(DeckVisibility.UNLISTED).user(owner).build());

        assertEquals(1, deckLikeService.like(linked.getId(), liker.getId()).getLikeCount());
    }

    @Test
    @DisplayName("좋아요 달린 덱 삭제 성공 — DB CASCADE가 좋아요 행도 제거 (V12 회귀)")
    void removeDeck_withLikes_cascades() {
        deckLikeService.like(publicDeck.getId(), liker.getId());

        // V12 이전엔 여기서 FK 위반 500 (deck_likes가 덱을 참조 중이라 부모 삭제 거부)
        deckService.remove(publicDeck.getId(), owner.getId());
        // DELETE를 지금 DB로 보냄 — 아래 exists 쿼리는 deck_likes만 봐서(decks와 query space 불일치)
        // 하이버네이트가 auto-flush를 건너뜀. 운영에선 커밋 시점 flush라 문제없는 테스트 전용 조치
        deckRepository.flush();

        assertFalse(deckRepository.findById(publicDeck.getId()).isPresent());
        assertFalse(deckLikeRepository.existsByUserIdAndDeckId(liker.getId(), publicDeck.getId()));
    }
}
