package com.vocamaster.card;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CardRepository extends JpaRepository<Card, Long> {
    List<Card> findByDeckId(Long deckId);
    List<Card> findByDeckIdAndStarredTrue(Long deckId);
    Page<Card> findByDeckId(Long deckId, Pageable pageable);
    Page<Card> findByDeckIdAndStarredTrue(Long deckId, Pageable pageable);
    long countByDeckId(Long deckId);
    long countByDeckIdAndStarredTrue(Long deckId);

    @Query("SELECT c FROM Card c WHERE c.deck.id = :deckId " +
    "AND (:keyword IS NULL OR LOWER(c.front) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
    "                       OR LOWER(c.back) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
    "AND (:starred IS NULL OR c.starred = :starred)")
    Page<Card> search(@Param("deckId") Long deckId,
                      @Param("keyword") String keyword,
                      @Param("starred") Boolean starred,
                      Pageable pageable);
}
