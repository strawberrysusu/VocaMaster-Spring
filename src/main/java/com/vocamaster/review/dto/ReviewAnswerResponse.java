package com.vocamaster.review.dto;

import com.vocamaster.review.CardProgress;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReviewAnswerResponse {

    private Long cardId;
    private int boxLevel;
    private int correctStreak;
    private int wrongCount;
    private LocalDateTime nextReviewAt;

    public static ReviewAnswerResponse from(CardProgress progress) {
        return ReviewAnswerResponse.builder()
                .cardId(progress.getCard().getId())
                .boxLevel(progress.getBoxLevel())
                .correctStreak(progress.getCorrectStreak())
                .wrongCount(progress.getWrongCount())
                .nextReviewAt(progress.getNextReviewAt())
                .build();
    }
}
