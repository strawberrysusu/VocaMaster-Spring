package com.vocamaster.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RegisterRequest {

    @Email
    @NotBlank
    @Size(max = 255)   // users.email varchar(255) — 넘으면 DB 오류가 '동시 요청' 409로 둔갑했다 (11/14 검토)
    private String email;

    @NotBlank
    @Size(min = 6, message = "비밀번호는 최소 6자 이상이어야 합니다")
    private String password;

    @NotBlank
    @Size(max = 30, message = "닉네임은 1~30자")   // 프로필 수정(UpdateMeRequest)과 같은 규칙 — 가입만 길이 제한이 없었다 (11/14 검토)
    private String nickname;
}
