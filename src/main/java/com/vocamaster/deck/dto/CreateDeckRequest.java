package com.vocamaster.deck.dto;

import com.vocamaster.common.ValidationPatterns;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateDeckRequest {

    @NotBlank
    @Pattern(regexp = ValidationPatterns.HAS_TEXT, message = "비워 둘 수 없습니다")   // 공백·안 보이는 글자만 있어도 빈칸 (수정·가져오기와 같은 규칙, 11/14)
    @Size(max = 255)          // decks.title varchar(255)
    private String title;

    @Size(max = 255)
    private String description;
}
