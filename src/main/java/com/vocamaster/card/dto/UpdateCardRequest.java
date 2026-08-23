package com.vocamaster.card.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateCardRequest {
    @Size(max = 255)
    private String front;
    @Size(max = 255)
    private String back;
    @Size(max = 200)
    private String reading;         // 읽기(요미가나) — 선택
    private String exampleSentence;
    private String memo;
    private Integer position;
}
