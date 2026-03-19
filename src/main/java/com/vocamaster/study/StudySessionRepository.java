package com.vocamaster.study;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {
    List<StudySession> findByDeckIdAndUserIdOrderByCreatedAtDesc(Long deckId, Long userId);
    long countByDeckIdAndUserIdAndCreatedAtAfter(Long deckId, Long userId, LocalDateTime after);
}
