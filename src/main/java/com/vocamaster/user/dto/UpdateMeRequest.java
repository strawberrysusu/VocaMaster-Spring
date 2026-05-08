package com.vocamaster.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMeRequest {

    @NotBlank
    @Size(min = 1, max = 30, message = "닉네임은 1~30자")
    private String nickname;
}
