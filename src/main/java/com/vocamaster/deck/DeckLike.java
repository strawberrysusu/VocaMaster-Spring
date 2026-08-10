package com.vocamaster.deck;

import com.vocamaster.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 좋아요 1건 = 1행 (ADR-032).
 * (user_id, deck_id) 복합 unique가 "1인 1좋아요"의 물리적 보증 — 멱등성의 최종 수문장.
 */
@Entity
@Table(name = "deck_likes",
        uniqueConstraints = @UniqueConstraint(name = "uq_deck_likes_user_deck",
                columnNames = {"user_id", "deck_id"}))
@Getter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DeckLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deck_id", nullable = false)
    private Deck deck;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
