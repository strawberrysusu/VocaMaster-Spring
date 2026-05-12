package com.vocamaster.card.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateCardRequest {
    private String front;
    private String back;
    private String exampleSentence;
    private String memo;
    private Integer position;
}
