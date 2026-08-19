package com.vocamaster.deck;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Set;

public interface DeckLikeRepository extends JpaRepository<DeckLike, Long> {

    boolean existsByUserIdAndDeckId(Long userId, Long deckId);

    // 목록의 likedByMe — 덱마다 exists 하면 N+1. IN 한 방으로 내가 누른 덱 id 집합만
    @Query("select l.deck.id from DeckLike l where l.user.id = :userId and l.deck.id in :deckIds")
    Set<Long> findLikedDeckIds(@Param("userId") Long userId, @Param("deckIds") Collection<Long> deckIds);

    // 반환값 = 지운 행 수. 0이면 원래 좋아요가 없던 것 — unlike 멱등성의 근거 (ADR-032)
    // @Transactional: 파생 delete는 트랜잭션 필수 — 서비스 밖(테스트 정리 등)에서 불려도 안전하게
    @Transactional
    long deleteByUserIdAndDeckId(Long userId, Long deckId);
}
