package com.vocamaster.review;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReviewSubmissionRepository extends JpaRepository<ReviewSubmission, Long> {

    /**
     * 제출 처리권 확보. 반환 1 = 내가 처리한다, 0 = 이미 처리됐거나 처리 중이다.
     *
     * <p>예외가 아니라 반환값으로 중복을 아는 이유: 같은 트랜잭션 안에서 unique 위반을 catch하면
     * 트랜잭션이 rollback-only로 표시되어 뒤이은 조회가 나가지 못한다.
     * deck_study_days의 하루 1회 보증(DeckStudyRankingListener)과 같은 관용구.</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO review_submissions
                (user_id, submission_id, answer_count, known_count, payload_hash, created_at)
            VALUES (:userId, :submissionId, :answerCount, :knownCount, :payloadHash, NOW(6))
            """, nativeQuery = true)
    int insertIgnore(@Param("userId") Long userId,
                     @Param("submissionId") String submissionId,
                     @Param("answerCount") int answerCount,
                     @Param("knownCount") int knownCount,
                     @Param("payloadHash") String payloadHash);

    /**
     * 처리권을 뺏긴 쪽이 확정본을 읽을 때 쓴다. <b>반드시 잠금 읽기</b>여야 한다 —
     * REPEATABLE READ에서 일반 SELECT는 자기 트랜잭션의 스냅숏을 보므로,
     * 이긴 쪽이 방금 커밋한 행을 못 볼 수 있다. (DeckRepository.findWithLockById와 같은 이유)
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT s FROM ReviewSubmission s WHERE s.user.id = :userId AND s.submissionId = :submissionId")
    Optional<ReviewSubmission> findLocking(@Param("userId") Long userId,
                                           @Param("submissionId") String submissionId);
}
