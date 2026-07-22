package com.vocamaster.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CardProgressRepository extends JpaRepository<CardProgress, Long> {

    Optional<CardProgress> findByUserIdAndCardId(Long userId, Long cardId);

    // 복습 대상(due) 조회 — join fetch로 카드까지 한 쿼리에 (N+1 방지).
    // 새 카드(progress 없음)는 CardProgress에서 시작하므로 애초에 후보가 아님 (A 결정)
    @Query("""
            select p from CardProgress p
            join fetch p.card
            where p.user.id = :userId
              and p.nextReviewAt <= :now
              and (:deckId is null or p.card.deck.id = :deckId)
            order by p.nextReviewAt asc
            """)
    List<CardProgress> findDueCards(@Param("userId") Long userId,
                                    @Param("deckId") Long deckId,
                                    @Param("now") LocalDateTime now);
}
