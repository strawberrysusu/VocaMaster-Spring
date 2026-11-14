package com.vocamaster.card;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vocamaster.deck.Deck;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

// @DynamicUpdate: 바뀐 컬럼만 UPDATE (Deck과 같은 처방). 없으면 별표 저장이 '읽었을 때의 설명(memo)'까지 같이 써서
// 그 사이 고친 설명이 되돌아가고, 설명 저장은 그 사이 켜진 별표를 끈다 (Codex 검산 2026-11-14, CardOverwriteTest)
@Entity
@DynamicUpdate
@Table(name = "cards")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String front;

    @Column(nullable = false)
    private String back;

    // 읽기(요미가나) — 会議 → かいぎ. 표시·🔊 전용, 채점엔 안 씀 (V14, 2026-08-23). 영어 덱은 null
    @Column(length = 200)
    private String reading;

    @Builder.Default
    private Boolean starred = false;

    @Column(name = "example_sentence", length = 500)
    private String exampleSentence;

    @Column(length = 500)
    private String memo;

    private Integer position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deck_id", nullable = false)
    @JsonIgnore
    private Deck deck;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
