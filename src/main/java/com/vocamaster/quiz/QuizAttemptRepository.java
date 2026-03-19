package com.vocamaster.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    List<QuizAttempt> findTop50ByDeckIdAndUserIdOrderByCreatedAtDesc(Long deckId, Long userId);
    long countByDeckIdAndUserId(Long deckId, Long userId);
    long countByDeckIdAndUserIdAndIsCorrectTrue(Long deckId, Long userId);
    long countByDeckIdAndUserIdAndCreatedAtAfter(Long deckId, Long userId, LocalDateTime after);

    // 오답 카드 ID 목록 (가장 최근에 틀린 카드)
    @Query("SELECT DISTINCT q.card.id FROM QuizAttempt q " +
           "WHERE q.deckId = :deckId AND q.user.id = :userId AND q.isCorrect = false")
    List<Long> findWrongCardIds(Long deckId, Long userId);
}
