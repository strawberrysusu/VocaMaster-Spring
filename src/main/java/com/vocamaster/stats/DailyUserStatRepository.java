package com.vocamaster.stats;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyUserStatRepository extends JpaRepository<DailyUserStat, Long> {

    Optional<DailyUserStat> findByUserIdAndStatDate(Long userId, LocalDate statDate);

    // 같은 날 답변이 동시에 와도 증가가 분실되지 않도록 DB에서 원자적으로 +1 (lost update 방지).
    // 반환값 = 수정된 행 수: 1이면 오늘 줄 있음, 0이면 오늘 첫 학습 (서비스에서 행 생성)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update DailyUserStat s
               set s.studyCount = s.studyCount + 1
             where s.user.id = :userId
               and s.statDate = :date
            """)
    int incrementStudyCount(@Param("userId") Long userId, @Param("date") LocalDate date);

    // 오늘 줄 생성 — 동시에 다른 요청이 먼저 만들었어도(UNIQUE 충돌) INSERT가 study_count +1로 전환됨 (MySQL upsert).
    // try/catch로 제약 위반을 잡는 방식은 위반 시점(커밋/flush)과 세션 오염 문제로 불안정 → DB에 맡기는 게 정석.
    // 동시 두 요청이 같은 streak을 계산해도(둘 다 어제 행 기준) 값이 같아서 안전
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into daily_user_stats (user_id, stat_date, study_count, streak)
            values (:userId, :date, 1, :streak)
            on duplicate key update study_count = study_count + 1
            """, nativeQuery = true)
    void upsertTodayRow(@Param("userId") Long userId,
                        @Param("date") LocalDate date,
                        @Param("streak") int streak);
}
