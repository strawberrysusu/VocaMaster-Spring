package com.vocamaster.card;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CardRepository extends JpaRepository<Card, Long> {
    List<Card> findByDeckId(Long deckId);
    List<Card> findByDeckIdAndStarredTrue(Long deckId);
    Page<Card> findByDeckId(Long deckId, Pageable pageable);
    Page<Card> findByDeckIdAndStarredTrue(Long deckId, Pageable pageable);
    long countByDeckId(Long deckId);
    long countByDeckIdAndStarredTrue(Long deckId);
}
