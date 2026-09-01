package com.vocamaster.user;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.auth.AuthService;
import com.vocamaster.auth.RefreshTokenRepository;
import com.vocamaster.auth.dto.LoginRequest;
import com.vocamaster.auth.dto.RegisterRequest;
import com.vocamaster.auth.dto.TokenPair;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.common.exception.UnauthorizedException;
import com.vocamaster.user.dto.ChangePasswordRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 비밀번호 변경 계약 (2026-09-01).
 *
 * <p>핵심은 <b>400과 401을 가르는 것</b>이다. 현재 비밀번호를 틀린 건 이미 로그인한 사용자의
 * '입력값 오류'지 인증 세션 문제가 아니다. 401로 던지면 프런트 공용 {@code api()}가
 * 토큰 만료로 오인해 refresh 후 같은 요청을 한 번 더 쏜다 — 비밀번호 오류에 refresh가 도는 건 틀렸다.</p>
 */
@AutoConfigureMockMvc
class ChangePasswordTest extends AbstractIntegrationTest {

    private static final String OLD = "Passw0rd!";
    private static final String NEW = "N3wPassw0rd!";

    @Autowired private MockMvc mvc;
    @Autowired private AuthService authService;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    private String uniqueEmail() {
        return "pw_" + System.nanoTime() + "@test.com";
    }

    private TokenPair register(String email) {
        RegisterRequest req = new RegisterRequest();
        req.setEmail(email);
        req.setPassword(OLD);
        req.setNickname("비번테스트");
        return authService.register(req, "test-ua", "127.0.0.1");
    }

    private ChangePasswordRequest body(String current, String next) {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword(current);
        req.setNewPassword(next);
        return req;
    }

    private TokenPair login(String email, String password) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return authService.login(req, "test-ua", "127.0.0.1");
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 400 — 401이면 프런트가 토큰 만료로 오인해 refresh를 돈다")
    void wrongCurrentPassword_isBadRequestNotUnauthorized() {
        String email = uniqueEmail();
        register(email);
        Long userId = userRepository.findByEmail(email).orElseThrow().getId();

        assertThrows(BadRequestException.class,
                () -> userService.changePassword(userId, body("WrongPassw0rd!", NEW)));

        // 비밀번호는 그대로 — 옛 비밀번호로 여전히 로그인된다
        assertNotNull(login(email, OLD));
    }

    @Test
    @DisplayName("access token이 없거나 위조면 401 — 비밀번호 오류(400)와 구분된다")
    void missingToken_isUnauthorized() throws Exception {
        mvc.perform(patch("/users/me/password")
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"" + OLD + "\",\"newPassword\":\"" + NEW + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("HTTP로도 현재 비밀번호 오류는 400")
    void wrongCurrentPassword_http400() throws Exception {
        String email = uniqueEmail();
        TokenPair pair = register(email);

        mvc.perform(patch("/users/me/password")
                        .header("Authorization", "Bearer " + pair.accessToken())
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"WrongPassw0rd!\",\"newPassword\":\"" + NEW + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("새 비밀번호가 현재와 같으면 400")
    void samePassword_rejected() {
        String email = uniqueEmail();
        register(email);
        Long userId = userRepository.findByEmail(email).orElseThrow().getId();

        assertThrows(BadRequestException.class,
                () -> userService.changePassword(userId, body(OLD, OLD)));
    }

    @Test
    @DisplayName("구글 가입자는 비밀번호가 없다 — '틀렸다'가 아니라 '사용하지 않는다'로 거절")
    void googleUser_rejectedWithClearReason() {
        User google = userRepository.save(User.builder()
                .email(uniqueEmail())
                .password(null)          // AuthService의 구글 신규 가입과 동일
                .nickname("구글")
                .provider("google")
                .build());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> userService.changePassword(google.getId(), body("anything", NEW)));
        assertTrue(ex.getMessage().contains("구글"), "실제 메시지: " + ex.getMessage());
    }

    @Test
    @DisplayName("정상 변경 — 옛 비밀번호는 막히고 새 비밀번호로 로그인되며 refresh 토큰은 전부 폐기")
    void changeSucceeds_oldBlocked_newWorks_refreshRevoked() {
        String email = uniqueEmail();
        register(email);                                   // 발급된 refresh 토큰 1개
        Long userId = userRepository.findByEmail(email).orElseThrow().getId();

        userService.changePassword(userId, body(OLD, NEW));

        // 옛 비밀번호는 더 이상 안 통한다
        assertThrows(UnauthorizedException.class, () -> login(email, OLD));

        // 새 비밀번호로는 로그인된다 (이 호출이 refresh 토큰을 새로 하나 만든다)
        assertNotNull(login(email, NEW));

        // 변경 시점에 있던 토큰은 폐기됐다 — 지금 유효한 건 방금 로그인으로 생긴 1개뿐
        assertEquals(1, refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(userId) && t.getRevokedAt() == null)
                .count());
    }
}
