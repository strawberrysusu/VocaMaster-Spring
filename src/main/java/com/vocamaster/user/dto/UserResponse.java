package com.vocamaster.user.dto;

import com.vocamaster.user.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String email;
    private String nickname;
    private String provider;        // local | google — 프로필 팝오버의 가입 경로 뱃지 (ADR-047)
    private LocalDateTime createdAt;

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getNickname(),
                user.getProvider(), user.getCreatedAt());
    }
}
