package com.vocamaster.typing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vocamaster.card.Card;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "typing_questions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class TypingQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    @JsonIgnore
    private TypingSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    @JsonIgnore
    private Card card;

    @Column(name = "question_order", nullable = false)
    private int questionOrder;                                  // 0 ~ N-1

    @Column(name = "question_text", nullable = false, length = 500)
    private String questionText;

    @Column(name = "correct_answer", nullable = false, length = 500)
    private String correctAnswer;                               // 쉼표 복수 정답 가능 ("사과, 능금")

    @Column(name = "typed_answer", length = 500)
    private String typedAnswer;                                 // NULL = 아직 안 풀음

    @Column(name = "is_correct")
    private Boolean isCorrect;                                  // NULL = 아직

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;                           // NULL = 아직
}
