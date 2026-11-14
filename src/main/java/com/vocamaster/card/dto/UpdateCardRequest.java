package com.vocamaster.card.dto;

import com.vocamaster.common.ValidationPatterns;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateCardRequest {
    // PATCH: 안 보낸 칸(null)은 그대로, 보낸 칸이 비었거나 공백·안 보이는 글자뿐이면 400 — 만들 때·가져오기와 같은 규칙
    // (ValidationPatterns.HAS_TEXT, Codex 검산 11/14 — 예전엔 빈 단어가 200으로 저장됐다)
    @Pattern(regexp = ValidationPatterns.HAS_TEXT, message = "비워 둘 수 없습니다")
    @Size(max = 255)
    private String front;
    @Pattern(regexp = ValidationPatterns.HAS_TEXT, message = "비워 둘 수 없습니다")
    @Size(max = 255)
    private String back;
    @Size(max = 200)
    private String reading;         // 읽기(요미가나) — 선택
    @Size(max = 500)          // cards.example_sentence varchar(500)
    private String exampleSentence;
    @Size(max = 500)
    private String memo;
    private Integer position;
}
