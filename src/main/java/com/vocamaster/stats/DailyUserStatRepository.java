package com.vocamaster.stats;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyUserStatRepository extends JpaRepository<DailyUserStat, Long> {

    // ── 학습 랭킹 (ADR-059) — 점수 = study_count(답한 카드 수). 탈퇴한 계정은 빠진다. 같은 점수면 먼저 가입한 사람이 위(정렬만, 순위는 같음)
    // 행 모양: [userId, nickname, rankingMasked, points]

    /** 그날의 랭킹 — 그날 줄만 점수 순 */
    @Query("select u.id, u.nickname, u.rankingMasked, s.studyCount from DailyUserStat s join s.user u " +
           "where s.statDate = :day and u.deletedAt is null and s.studyCount > 0 " +
           "order by s.studyCount desc, u.id asc")
    List<Object[]> rankDay(@Param("day") LocalDate day, Pageable page);

    /** 전체 랭킹 — 지금까지의 합계 순 */
    @Query("select u.id, u.nickname, u.rankingMasked, sum(s.studyCount) from DailyUserStat s join s.user u " +
           "where u.deletedAt is null group by u.id, u.nickname, u.rankingMasked having sum(s.studyCount) > 0 " +
           "order by sum(s.studyCount) desc, u.id asc")
    List<Object[]> rankTotal(Pageable page);

    /** 내 순위 = 나보다 점수가 높은 사람 수 + 1 (그날) */
    @Query("select count(s) from DailyUserStat s join s.user u " +
           "where s.statDate = :day and u.deletedAt is null and s.studyCount > :points")
    long countDayAbove(@Param("day") LocalDate day, @Param("points") long points);

    /** 내 순위 = 나보다 합계가 높은 사람 수 + 1 (전체) — 그룹 수를 세는 JPQL이 없어 합계 목록의 길이로 센다 */
    @Query("select sum(s.studyCount) from DailyUserStat s join s.user u " +
           "where u.deletedAt is null group by u.id having sum(s.studyCount) > :points")
    List<Long> totalsAbove(@Param("points") long points);

    Optional<DailyUserStat> findByUserIdAndStatDate(Long userId, LocalDate statDate);

    // 통계 화면 — 기간 내 일별 행 (빈 날은 서비스가 0으로 채움)
    List<DailyUserStat> findByUserIdAndStatDateBetweenOrderByStatDateAsc(Long userId, LocalDate from, LocalDate to);

    // 통계 화면 — 누적 집계 한 방: [총 학습 수, 최고 연속, 활동일 수]
    @Query("select coalesce(sum(s.studyCount), 0), coalesce(max(s.streak), 0), count(s) " +
           "from DailyUserStat s where s.user.id = :userId")
    List<Object[]> aggregate(@Param("userId") Long userId);   // 단일 행도 List로 — Object[] 반환형은 행이 한 겹 더 싸여 ClassCast (실측)

    // (제거됨 2026-08-22) 0행 매치 UPDATE로 '오늘 줄 있나' 탐색하던 incrementStudyCount —
    // InnoDB 갭 락 데드락의 원인. 이제 upsertTodayRow 한 방이 두 경우를 모두 처리한다.

    // 오늘 줄 생성 — 동시에 다른 요청이 먼저 만들었어도(UNIQUE 충돌) INSERT가 study_count +1로 전환됨 (MySQL upsert).
    // try/catch로 제약 위반을 잡는 방식은 위반 시점(커밋/flush)과 세션 오염 문제로 불안정 → DB에 맡기는 게 정석.
    // 동시 두 요청이 같은 streak을 계산해도(둘 다 어제 행 기준) 값이 같아서 안전
    // count는 '이번에 더할 답변 수'. 단건 경로는 1, 일괄 제출(V21)은 답변 수만큼 한 번에 더한다 —
    // 예전처럼 +1 고정이면 200장 세션이 200번 UPDATE를 쳐야 한다 (2026-08-31)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into daily_user_stats (user_id, stat_date, study_count, streak)
            values (:userId, :date, :count, :streak)
            on duplicate key update study_count = study_count + :count
            """, nativeQuery = true)
    void upsertTodayRow(@Param("userId") Long userId,
                        @Param("date") LocalDate date,
                        @Param("streak") int streak,
                        @Param("count") int count);
}
