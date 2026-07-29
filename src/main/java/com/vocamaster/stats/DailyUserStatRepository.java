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
}
