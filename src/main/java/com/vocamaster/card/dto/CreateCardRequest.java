package com.vocamaster.card.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateCardRequest {

    @NotBlank
    private String front;

    @NotBlank
    private String back;
    @Size(max = 200)
    private String reading;         // 읽기(요미가나) — 선택
    private String exampleSentence;
    private String memo;
    private Integer position;
}
