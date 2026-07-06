package com.vocamaster.review;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vocamaster.card.Card;
import com.vocamaster.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Leitner Box 학습 진행도 (ADR-029).
 *
 * 유저-카드 쌍마다 1행. 핵심 2필드:
 * - boxLevel (1~6): 클수록 잘 외운 카드 → 복습 간격 김
 * - nextReviewAt: 이 시각이 지나면 due (복습 대상)
 *
 * 행이 없는 카드 = 아직 복습 시작 전 → 첫 답변 시 ReviewService가 생성 (box=1).
 * 박스 증감/간격 계산은 전부 ReviewService 책임 (엔티티는 상태만 보관).
 */
@Entity
@Table(name = "card_progress",
        uniqueConstraints = @UniqueConstraint(name = "uq_card_progress_user_card",
                columnNames = {"user_id", "card_id"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CardProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    @JsonIgnore
    private Card card;

    @Column(name = "box_level", nullable = false)
    private int boxLevel;

    @Column(name = "next_review_at", nullable = false)
    private LocalDateTime nextReviewAt;

    @Column(name = "correct_streak", nullable = false)
    private int correctStreak;

    @Column(name = "wrong_count", nullable = false)
    private int wrongCount;

    @Column(name = "last_reviewed_at")
    private LocalDateTime lastReviewedAt;           // NULL = 생성만 되고 아직 답변 전

    @Version
    @Column(nullable = false)
    private Long version;                           // 낙관적 락 — Hibernate가 관리 (직접 수정 금지)
}
