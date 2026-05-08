package com.vocamaster.user;

import com.vocamaster.auth.CustomUserDetails;
import com.vocamaster.user.dto.ChangePasswordRequest;
import com.vocamaster.user.dto.UpdateMeRequest;
import com.vocamaster.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User - 회원")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회")
    public UserResponse getMe(@AuthenticationPrincipal CustomUserDetails me) {
        return userService.getMe(me.getUserId());
    }

    @PatchMapping("/me")
    @Operation(summary = "내 정보 수정 (닉네임)")
    public UserResponse updateMe(@AuthenticationPrincipal CustomUserDetails me,
                                 @Valid @RequestBody UpdateMeRequest req) {
        return userService.updateMe(me.getUserId(), req);
    }

    @PatchMapping("/me/password")
    @Operation(summary = "비밀번호 변경 (모든 세션 강제 로그아웃)")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal CustomUserDetails me,
                                               @Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(me.getUserId(), req);
        return ResponseEntity.noContent().build();
    }
}
