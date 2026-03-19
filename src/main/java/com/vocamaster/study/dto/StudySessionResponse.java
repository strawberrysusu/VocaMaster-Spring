package com.vocamaster.study.dto;

import com.vocamaster.card.dto.CardResponse;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class StudySessionResponse {

    private Long sessionId;
    private String direction;
    private List<CardResponse> cards;
    private int totalCards;
}
