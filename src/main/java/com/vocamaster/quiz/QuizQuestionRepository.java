package com.vocamaster.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    List<QuizQuestion> findBySessionIdOrderByQuestionOrderAsc(Long sessionId);

    // 세션 방식의 누적 오답 카드 — quiz_attempts(구형 단건)와 합쳐 '누적 오답'을 만든다.
    // 두 장부가 따로 있어 wrongOnly가 세션 오답을 못 보던 불일치 수리 (Codex 검산 2026-08-23)
    @Query("select distinct q.card.id from QuizQuestion q " +
           "where q.session.deck.id = :deckId and q.session.user.id = :userId and q.isCorrect = false")
    List<Long> findWrongCardIds(@Param("deckId") Long deckId, @Param("userId") Long userId);

    // 통합 오답노트용 시간 필터 (QuizAttemptRepository.findWrongCardIdsSince와 짝)
    @Query("select distinct q.card.id from QuizQuestion q " +
           "where q.session.deck.id = :deckId and q.session.user.id = :userId and q.isCorrect = false " +
           "and q.answeredAt >= :since")
    List<Long> findWrongCardIdsSince(@Param("deckId") Long deckId, @Param("userId") Long userId,
                                     @Param("since") java.time.LocalDateTime since);
}
