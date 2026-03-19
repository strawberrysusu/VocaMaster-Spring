package com.vocamaster.study.dto;

import com.vocamaster.card.dto.CardResponse;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class StudySummaryResponse {

    private Long sessionId;
    private String deckTitle;
    private String direction;
    private long total;
    private long known;
    private long unknown;
    private long accuracy;
    private List<CardResponse> unknownCards;
}
