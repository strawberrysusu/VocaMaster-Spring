package com.vocamaster.deck;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface DeckStudyDayRepository extends JpaRepository<DeckStudyDay, Long> {

    /**
     * 반환값 = 영향 행 수. 1이면 오늘 첫 학습(점수 대상), 0이면 이미 있던 것(unique에 걸려 무시).
     * 예외를 트랜잭션 안에서 잡는 대신 DB가 조용히 거르고 행 수로 알려주는 방식 (Codex 검산).
     * MySQL 전용 문법 — 테스트도 MySQL 컨테이너라 동일 경로.
     */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO deck_study_days (user_id, deck_id, stat_date, created_at)
            VALUES (:userId, :deckId, :date, NOW(6))
            """, nativeQuery = true)
    int insertIgnore(@Param("userId") Long userId, @Param("deckId") Long deckId, @Param("date") LocalDate date);

    long countByDeckId(Long deckId);
}
