package com.vocamaster.quiz;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuizSessionRepository extends JpaRepository<QuizSession, Long> {

    // 답 제출 직렬화 — 세션 행 X 잠금(FOR UPDATE). 같은 세션의 동시 제출이 한 줄로 서서
    // "같은 문제 2회 반영"·"마지막 두 문제 동시 제출 시 endedAt 누락"이 구조적으로 불가능 (Codex 검산 2026-08-23)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from QuizSession s where s.id = :id")
    Optional<QuizSession> findWithLockById(@Param("id") Long id);

    // 덱 통계: 최근 7일 퀴즈 세션 수 (attempts 기반에서 전환, 8/28)
    long countByDeckIdAndUserIdAndStartedAtAfter(Long deckId, Long userId, java.time.LocalDateTime after);
}
