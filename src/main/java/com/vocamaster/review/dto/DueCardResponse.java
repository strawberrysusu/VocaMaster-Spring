package com.vocamaster.review.dto;

import com.vocamaster.review.CardProgress;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DueCardResponse {

    private Long cardId;
    private String front;
    private String back;
    private String reading;     // 읽기(요미가나), 없으면 null (V14)
    private Boolean starred;    // ⭐ 표시 여부 — 없으면 복습 화면의 별이 항상 꺼진 채로 뜬다 (2026-08-31)
    private int boxLevel;
    private LocalDateTime nextReviewAt;

    public static DueCardResponse from(CardProgress progress) {
        return DueCardResponse.builder()
                .cardId(progress.getCard().getId())
                .front(progress.getCard().getFront())
                .back(progress.getCard().getBack())
                .reading(progress.getCard().getReading())
                .starred(progress.getCard().getStarred())
                .boxLevel(progress.getBoxLevel())
                .nextReviewAt(progress.getNextReviewAt())
                .build();
    }
}
