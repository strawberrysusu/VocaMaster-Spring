package com.vocamaster.review;

import com.vocamaster.review.dto.BoxCountResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CardProgressRepository extends JpaRepository<CardProgress, Long> {

    Optional<CardProgress> findByUserIdAndCardId(Long userId, Long cardId);

    // 복습 대상(due) 조회 — join fetch로 카드까지 한 쿼리에 (N+1 방지).
    // 새 카드(progress 없음)는 CardProgress에서 시작하므로 애초에 후보가 아님 (A 결정)
    @Query("""
            select p from CardProgress p
            join fetch p.card
            where p.user.id = :userId
              and p.nextReviewAt <= :now
              and (:deckId is null or p.card.deck.id = :deckId)
            order by p.nextReviewAt asc
            """)
    List<CardProgress> findDueCards(@Param("userId") Long userId,
                                    @Param("deckId") Long deckId,
                                    @Param("now") LocalDateTime now);

    // dueCount — 카운트는 카드 내용을 안 만지므로 N+1 걱정이 없어 파생 메서드로 충분 (목록 조회와의 차이)
    long countByUserIdAndNextReviewAtLessThanEqual(Long userId, LocalDateTime now);

    // reviewedTodayCount — 오늘 복습 답변한 '서로 다른 카드' 수.
    // 반열림 구간 [오늘 00:00, 내일 00:00): 시작 포함, 끝 제외 — 내일 0시 정각은 내일 몫
    @Query("""
            select count(p) from CardProgress p
            where p.user.id = :userId
              and p.lastReviewedAt >= :start
              and p.lastReviewedAt < :end
            """)
    long countReviewedBetween(@Param("userId") Long userId,
                              @Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);

    // 박스별 분포 — 생성자 표현식으로 DTO 직행 (있는 박스만 반환됨, 0 채우기는 서비스 몫)
    @Query("""
            select new com.vocamaster.review.dto.BoxCountResponse(p.boxLevel, count(p))
            from CardProgress p
            where p.user.id = :userId
            group by p.boxLevel
            """)
    List<BoxCountResponse> countByBoxLevel(@Param("userId") Long userId);

    // 통계 화면 — 덱별 [deckId, 시작한 카드 수, 숙달(박스 >= :masteredBox) 수] GROUP BY 한 방 (덱마다 count 도는 N+1 회피)
    @Query("""
            select c.deck.id, count(p), sum(case when p.boxLevel >= :masteredBox then 1 else 0 end)
            from CardProgress p join p.card c
            where p.user.id = :userId
            group by c.deck.id
            """)
    List<Object[]> progressByDeck(@Param("userId") Long userId, @Param("masteredBox") int masteredBox);
}
