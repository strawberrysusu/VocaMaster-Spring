package com.vocamaster.typing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface TypingQuestionRepository extends JpaRepository<TypingQuestion, Long> {

    List<TypingQuestion> findBySessionIdOrderByQuestionOrderAsc(Long sessionId);

    // ADR-028: 오답 카드 ID 목록 — 시간 필터 (통합 오답노트용)
    @Query("SELECT DISTINCT q.card.id FROM TypingQuestion q " +
           "WHERE q.session.deck.id = :deckId AND q.session.user.id = :userId " +
           "AND q.isCorrect = false AND q.answeredAt >= :since")
    List<Long> findWrongCardIdsByDeckIdAndUserIdSince(Long deckId, Long userId, LocalDateTime since);
}
