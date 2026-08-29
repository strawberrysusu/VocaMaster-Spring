package com.vocamaster.deck;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vocamaster.card.Card;
import com.vocamaster.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "decks")
// @DynamicUpdate: 바뀐 컬럼만 UPDATE. 없으면 제목 수정이 copy/like/study_count까지 "읽었을 때 값"으로 같이 써서,
// 그 사이 원자적 +1 된 카운터가 증발한다 (Codex 전수 감사 2026-08-23 — 카운터 덮어쓰기)
@DynamicUpdate
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Deck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DeckVisibility visibility = DeckVisibility.PRIVATE;

    // 📁 분류 폴더 (V20). 연관 대신 값 참조 — 폴더 삭제 시 DB FK(SET NULL)가 미분류로 정리
    private Long folderId;

    @Column(nullable = false)
    private long copyCount;

    @Column(nullable = false)
    private long likeCount;

    // 인기 점수 study 항 — "사용자×날짜 1회" 누적 학습자-일수, 답변 수 아님 (ADR-038)
    @Column(nullable = false)
    private long studyCount;

    // 복사 출처 추적 — 자기참조 FK, 원본 삭제 시 DB가 SET NULL (ADR-031)
    @Column(name = "original_deck_id")
    private Long originalDeckId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @OneToMany(mappedBy = "deck", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore
    private List<Card> cards = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
