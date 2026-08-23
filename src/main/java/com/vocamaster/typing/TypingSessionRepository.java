package com.vocamaster.typing;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TypingSessionRepository extends JpaRepository<TypingSession, Long> {

    // 답 제출 직렬화 — 세션 행 X 잠금 (QuizSessionRepository와 동일 이유)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TypingSession s where s.id = :id")
    Optional<TypingSession> findWithLockById(@Param("id") Long id);
}
