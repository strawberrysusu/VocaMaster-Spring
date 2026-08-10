package com.vocamaster.deck;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DeckRepository extends JpaRepository<Deck, Long> {
    List<Deck> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 공개 검색 (ADR-030). 메서드 이름 파생 대신 JPQL — And/Or 우선순위로 비공개 덱이 새는 사고 방지 (괄호 명시).
    // join fetch: 목록의 작성자 닉네임 N+1 차단. fetch 붙은 쿼리는 count 자동 파생이 안 돼 countQuery 별도.
    @Query(value = """
            select d from Deck d join fetch d.user
            where d.visibility = :visibility
              and (:keyword is null
                   or lower(d.title) like lower(concat('%', :keyword, '%'))
                   or lower(d.description) like lower(concat('%', :keyword, '%')))
            order by d.createdAt desc
            """,
           countQuery = """
            select count(d) from Deck d
            where d.visibility = :visibility
              and (:keyword is null
                   or lower(d.title) like lower(concat('%', :keyword, '%'))
                   or lower(d.description) like lower(concat('%', :keyword, '%')))
            """)
    Page<Deck> searchByVisibility(@Param("visibility") DeckVisibility visibility,
                                  @Param("keyword") String keyword,
                                  Pageable pageable);

    // 복사 카운터 — read-modify-write의 lost update 방지, 더하기를 DB가 직접 수행 (ADR-031)
    // flush: UPDATE 전에 밀린 INSERT를 먼저 반영 / clear: 벌크 UPDATE가 우회한 1차 캐시를 비움 (알려진 함정)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Deck d set d.copyCount = d.copyCount + 1 where d.id = :id")
    int incrementCopyCount(@Param("id") Long id);

    // 좋아요 카운터 (ADR-032) — 복사와 동일하게 원자적, 호출 순서도 동일 (X락 먼저)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Deck d set d.likeCount = d.likeCount + 1 where d.id = :id")
    int incrementLikeCount(@Param("id") Long id);

    // likeCount > 0 조건은 이론상 불필요(지운 행이 있을 때만 호출)하나 음수 방어 겸 명시
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Deck d set d.likeCount = d.likeCount - 1 where d.id = :id and d.likeCount > 0")
    int decrementLikeCount(@Param("id") Long id);
}
