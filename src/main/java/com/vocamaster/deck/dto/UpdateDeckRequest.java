package com.vocamaster.deck.dto;

import com.vocamaster.common.ValidationPatterns;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateDeckRequest {
    // 보낸 제목이 비었거나 공백뿐이면 400 — 공개 덱이면 탐색에 빈 제목이 보였다 (11/14 검토, 카드 수정과 같은 규칙)
    @Pattern(regexp = ValidationPatterns.HAS_TEXT, message = "비워 둘 수 없습니다")
    @Size(max = 255)          // DB 컬럼(varchar 255)과 일치 — 초과 입력은 500이 아니라 400 (Codex 검산 8/29)
    private String title;
    @Size(max = 255)
    private String description;
}
