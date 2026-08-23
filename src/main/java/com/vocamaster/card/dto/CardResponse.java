package com.vocamaster.card.dto;

import com.vocamaster.card.Card;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CardResponse {

    private Long id;
    private String front;
    private String back;
    private String reading;     // 읽기(요미가나), 없으면 null
    private Boolean starred;
    private String exampleSentence;
    private String memo;
    private Integer position;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CardResponse from(Card card) {
        return CardResponse.builder()
                .id(card.getId())
                .front(card.getFront())
                .back(card.getBack())
                .reading(card.getReading())
                .starred(card.getStarred())
                .exampleSentence(card.getExampleSentence())
                .memo(card.getMemo())
                .position(card.getPosition())
                .createdAt(card.getCreatedAt())
                .updatedAt(card.getUpdatedAt())
                .build();
    }
}
