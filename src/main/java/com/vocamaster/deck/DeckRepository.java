package com.vocamaster.deck;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
