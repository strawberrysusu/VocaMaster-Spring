package com.vocamaster.quiz;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vocamaster.card.Card;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_questions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    @JsonIgnore
    private QuizSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    @JsonIgnore
    private Card card;

    @Column(name = "question_order", nullable = false)
    private int questionOrder;                                  // 0 ~ N-1

    @Column(name = "question_text", nullable = false, length = 500)
    private String questionText;

    @Column(name = "choices_json", nullable = false, columnDefinition = "json")
    private String choicesJson;                                 // ["사과","바나나","체리","포도"]  — ObjectMapper로 직렬화

    @Column(name = "correct_answer", nullable = false, length = 500)
    private String correctAnswer;

    @Column(name = "selected_answer", length = 500)
    private String selectedAnswer;                              // NULL = 아직 안 풀음

    @Column(name = "is_correct")
    private Boolean isCorrect;                                  // NULL = 아직, true/false = 결과

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;                           // NULL = 아직
}
