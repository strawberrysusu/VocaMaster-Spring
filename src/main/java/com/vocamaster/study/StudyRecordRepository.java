package com.vocamaster.study;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface StudyRecordRepository extends JpaRepository<StudyRecord, Long> {

    // ADR-028: "모름" 카드 ID 목록 — 시간 필터 (통합 오답노트용)
    @Query("SELECT DISTINCT r.card.id FROM StudyRecord r " +
           "WHERE r.session.deck.id = :deckId AND r.session.user.id = :userId " +
           "AND r.known = false AND r.createdAt >= :since")
    List<Long> findUnknownCardIdsByDeckIdAndUserIdSince(Long deckId, Long userId, LocalDateTime since);
}
