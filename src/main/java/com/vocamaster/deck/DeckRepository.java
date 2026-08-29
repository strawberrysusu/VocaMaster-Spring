package com.vocamaster.deck;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface DeckRepository extends JpaRepository<Deck, Long> {
    List<Deck> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 공개 검색 (ADR-030). 메서드 이름 파생 대신 JPQL — And/Or 우선순위로 비공개 덱이 새는 사고 방지 (괄호 명시).
    // join fetch: 목록의 작성자 닉네임 N+1 차단. fetch 붙은 쿼리는 count 자동 파생이 안 돼 countQuery 별도.
    @Query(value = """
            select d from Deck d join fetch d.user
            where d.visibility = :visibility
              and d.user.deletedAt is null
              and (:keyword is null
                   or lower(d.title) like lower(concat('%', :keyword, '%'))
                   or lower(d.description) like lower(concat('%', :keyword, '%')))
            order by d.createdAt desc, d.id desc
            """,
           countQuery = """
            select count(d) from Deck d
            where d.visibility = :visibility
              and d.user.deletedAt is null
              and (:keyword is null
                   or lower(d.title) like lower(concat('%', :keyword, '%'))
                   or lower(d.description) like lower(concat('%', :keyword, '%')))
            """)
    Page<Deck> searchByVisibility(@Param("visibility") DeckVisibility visibility,
                                  @Param("keyword") String keyword,
                                  Pageable pageable);

    // 인기 정렬 (ADR-033 → ADR-038): like×5 + copy×3 + study×1, 동점은 최신순.
    // ★ 가중치는 DeckRankingService 상수와 반드시 일치 (JPQL은 자바 상수를 못 읽어 두 곳에 존재 — 드리프트 주의)
    // 계산식 정렬은 인덱스를 못 타는 filesort — 현 규모 무해, 대규모엔 Redis ZSET 후보 (Phase 5)
    @Query(value = """
            select d from Deck d join fetch d.user
            where d.visibility = :visibility
              and d.user.deletedAt is null
              and (:keyword is null
                   or lower(d.title) like lower(concat('%', :keyword, '%'))
                   or lower(d.description) like lower(concat('%', :keyword, '%')))
            order by (d.likeCount * 5 + d.copyCount * 3 + d.studyCount * 1) desc, d.createdAt desc, d.id desc
            """,
           countQuery = """
            select count(d) from Deck d
            where d.visibility = :visibility
              and d.user.deletedAt is null
              and (:keyword is null
                   or lower(d.title) like lower(concat('%', :keyword, '%'))
                   or lower(d.description) like lower(concat('%', :keyword, '%')))
            """)
    Page<Deck> searchByVisibilityPopular(@Param("visibility") DeckVisibility visibility,
                                         @Param("keyword") String keyword,
                                         Pageable pageable);

    // 랭킹 재구축·전체 개수용 (ADR-035). 탈퇴 소유자 제외판 — 캐시 total 뻥튀기·유령 ZSET 멤버 방지 (Codex 검산 8/29)
    List<Deck> findByVisibilityAndUser_DeletedAtIsNull(DeckVisibility visibility);

    long countByVisibilityAndUser_DeletedAtIsNull(DeckVisibility visibility);

    // 캐시가 준 id들을 PUBLIC 조건으로 재검증하며 로드 (ADR-035 — 권한 판단은 항상 DB).
    // IN 결과는 입력 순서를 보장하지 않음 — 호출자가 Redis 순서로 재조립해야 함
    @Query("select d from Deck d join fetch d.user where d.id in :ids and d.visibility = :visibility and d.user.deletedAt is null")
    List<Deck> findByIdInAndVisibilityWithUser(@Param("ids") List<Long> ids,
                                               @Param("visibility") DeckVisibility visibility);

    // 복사 카운터 — read-modify-write의 lost update 방지, 더하기를 DB가 직접 수행 (ADR-031)
    // flush: UPDATE 전에 밀린 INSERT를 먼저 반영 / clear: 벌크 UPDATE가 우회한 1차 캐시를 비움 (알려진 함정)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Deck d set d.copyCount = d.copyCount + 1 where d.id = :id")
    int incrementCopyCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Deck d set d.studyCount = d.studyCount + 1 where d.id = :id")
    int incrementStudyCount(@Param("id") Long id);

    // SELECT ... FOR UPDATE — 대상 덱의 X 잠금을 '먼저' 잡기 위해 (자식 INSERT의 FK S 잠금 → X 승급 데드락 차단, ADR-031/038)
    // join fetch 없음: 붙이면 FOR UPDATE가 users 행까지 잠금. 주인 판별은 LAZY 프록시의 getId()로 충분 (쿼리 X)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Deck d where d.id = :id")
    Optional<Deck> findWithLockById(@Param("id") Long id);

    // 좋아요 카운터 (ADR-032) — 복사와 동일하게 원자적, 호출 순서도 동일 (X락 먼저)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Deck d set d.likeCount = d.likeCount + 1 where d.id = :id")
    int incrementLikeCount(@Param("id") Long id);

    // likeCount > 0 조건은 이론상 불필요(지운 행이 있을 때만 호출)하나 음수 방어 겸 명시
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Deck d set d.likeCount = d.likeCount - 1 where d.id = :id and d.likeCount > 0")
    int decrementLikeCount(@Param("id") Long id);
}
