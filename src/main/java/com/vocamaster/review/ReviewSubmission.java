package com.vocamaster.review;

import com.vocamaster.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 일괄 제출 영수증 (V21, 2026-08-31).
 *
 * <p>학습 세션의 답변은 완료 시점에 한 번에 들어온다. 이 표가 하는 일은 딱 하나 —
 * <b>같은 제출이 두 번 진행도를 움직이지 못하게</b> 막는 것. 더블클릭·네트워크 재시도·새로고침 후 재전송이 대상이다.</p>
 *
 * <p>쓰기는 JPA가 아니라 {@link ReviewSubmissionRepository#insertIgnore}(네이티브 INSERT IGNORE)로 한다.
 * 같은 트랜잭션 안에서 unique 위반을 예외로 받아 catch하면 그 트랜잭션이 이미 rollback-only가 되어
 * 뒤이은 조회가 못 나간다. 반환값 0으로 중복을 아는 쪽이 안전하다
 * (deck_study_days의 하루 1회 보증과 같은 관용구 — DeckStudyRankingListener 참고).</p>
 */
@Entity
@Table(name = "review_submissions",
        uniqueConstraints = @UniqueConstraint(name = "uq_review_submissions",
                columnNames = {"user_id", "submission_id"}))
@Getter
@NoArgsConstructor @AllArgsConstructor
public class ReviewSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 클라이언트가 세션 시작 때 1회 생성하는 UUID. 재전송 판별의 유일한 키 */
    @Column(name = "submission_id", nullable = false, length = 36)
    private String submissionId;

    @Column(name = "answer_count", nullable = false)
    private int answerCount;

    @Column(name = "known_count", nullable = false)
    private int knownCount;

    /**
     * 정렬된 답안의 SHA-256. 같은 submissionId로 <b>다른 답</b>이 오면 409로 거르기 위한 것.
     * 없으면, 응답만 유실된 뒤 사용자가 답을 고쳐 재전송했을 때 바뀐 답이 조용히 버려진다.
     */
    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
