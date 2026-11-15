package com.vocamaster.user.dto;

import com.vocamaster.common.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMeRequest {

    @NotBlank
    @Pattern(regexp = ValidationPatterns.HAS_TEXT, message = "비워 둘 수 없습니다")   // 전각 공백·안 보이는 글자뿐인 닉네임은 랭킹·공개 덱 작성자 칸이 빈 줄로 보였다 (11/15 점검)
    @Size(min = 1, max = 30, message = "닉네임은 1~30자")
    private String nickname;
}
