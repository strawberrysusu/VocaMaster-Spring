package com.vocamaster.deck;

import com.vocamaster.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 랭킹용 출석부 1행 = "이 사용자가 이 (원본) 덱으로 이 날짜에 공부했다" (ADR-038).
 * (user, deck, date) unique가 "하루 최대 1점"의 물리적 보증 — 좋아요(ADR-032)와 같은 패턴.
 * 쓰기는 native INSERT IGNORE(영향 행 수로 신규 판단)라 이 엔티티는 조회·정리용.
 */
@Entity
@Table(name = "deck_study_days",
        uniqueConstraints = @UniqueConstraint(name = "uq_deck_study_days",
                columnNames = {"user_id", "deck_id", "stat_date"}))
@Getter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DeckStudyDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deck_id", nullable = false)
    private Deck deck;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
