package com.vocamaster.card.dto;

import com.vocamaster.common.ValidationPatterns;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateCardRequest {

    @NotBlank
    @Pattern(regexp = ValidationPatterns.HAS_TEXT, message = "비워 둘 수 없습니다")   // 공백·안 보이는 글자만 있어도 빈칸 (수정·가져오기와 같은 규칙, 11/14)
    @Size(max = 255)
    private String front;

    @NotBlank
    @Pattern(regexp = ValidationPatterns.HAS_TEXT, message = "비워 둘 수 없습니다")   // 공백·안 보이는 글자만 있어도 빈칸 (수정·가져오기와 같은 규칙, 11/14)
    @Size(max = 255)
    private String back;
    @Size(max = 200)
    private String reading;         // 읽기(요미가나) — 선택
    @Size(max = 500)          // cards.example_sentence varchar(500) — 초과는 500이 아니라 400 (Codex 검산 8/29)
    private String exampleSentence;
    @Size(max = 500)
    private String memo;
    private Integer position;
}
