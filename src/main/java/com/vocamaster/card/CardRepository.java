package com.vocamaster.card;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface CardRepository extends JpaRepository<Card, Long> {

    /**
     * 별표 뒤집기를 DB가 한 번에 (11/14 검토) — 읽고(꺼짐) → 뒤집어(켜짐) 쓰면, 겹친 두 요청(빠른 두 번 클릭·두 탭)이
     * 같은 값을 읽어 한 번만 뒤집힌다. 옛 데이터의 null은 '꺼짐'으로 본다(→ 켜짐).
     * 벌크 UPDATE는 @UpdateTimestamp를 건너뛰어 updatedAt을 직접 넣는다. clearAutomatically: 뒤이은 조회가 새 값을 읽게
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Card c set c.starred = (case when c.starred = true then false else true end), c.updatedAt = :now where c.id = :id")
    int toggleStarred(@Param("id") Long id, @Param("now") LocalDateTime now);
    List<Card> findByDeckId(Long deckId);
    /** 이 덱의 카드 중 id가 ids에 든 것만 — 별표 일괄 설정이 '이 덱 카드만' 바꾸는 데 쓴다 (ADR-053) */
    List<Card> findByDeckIdAndIdIn(Long deckId, Collection<Long> ids);
    List<Card> findByDeckIdAndStarredTrue(Long deckId);
    Page<Card> findByDeckId(Long deckId, Pageable pageable);
    Page<Card> findByDeckIdAndStarredTrue(Long deckId, Pageable pageable);
    long countByDeckId(Long deckId);
    long countByDeckIdAndStarredTrue(Long deckId);

    // 통계 화면 — 내 덱 전체의 [deckId, 카드 수] GROUP BY 한 방
    @Query("select c.deck.id, count(c) from Card c where c.deck.user.id = :userId group by c.deck.id")
    List<Object[]> countByDeckForUser(@Param("userId") Long userId);

    @Query("SELECT c FROM Card c WHERE c.deck.id = :deckId " +
    "AND (:keyword IS NULL OR LOWER(c.front) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
    "                       OR LOWER(c.back) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
    "AND (:starred IS NULL OR c.starred = :starred)")
    Page<Card> search(@Param("deckId") Long deckId,
                      @Param("keyword") String keyword,
                      @Param("starred") Boolean starred,
                      Pageable pageable);
}
