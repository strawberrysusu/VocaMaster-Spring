package com.vocamaster.auth;

import com.vocamaster.auth.dto.LoginRequest;
import com.vocamaster.auth.dto.RegisterRequest;
import com.vocamaster.auth.dto.TokenResponse;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtProvider jwtProvider;

    private RegisterRequest registerRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("test@example.com");
        req.setPassword("password123");
        req.setNickname("tester");
        return req;
    }

    @Test
    @DisplayName("회원가입 성공 - 토큰 반환")
    void register_success() {
        TokenResponse response = authService.register(registerRequest());

        assertNotNull(response.getAccessToken());
        assertTrue(jwtProvider.validate(response.getAccessToken()));
        assertTrue(userRepository.existsByEmail("test@example.com"));
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    void register_duplicateEmail() {
        authService.register(registerRequest());

        assertThrows(IllegalArgumentException.class, () ->
                authService.register(registerRequest()));
    }

    @Test
    @DisplayName("로그인 성공")
    void login_success() {
        authService.register(registerRequest());

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("test@example.com");
        loginReq.setPassword("password123");

        TokenResponse response = authService.login(loginReq);

        assertNotNull(response.getAccessToken());
        Long userId = jwtProvider.getUserId(response.getAccessToken());
        assertEquals(1L, userRepository.count());
        assertNotNull(userId);
    }

    @Test
    @DisplayName("로그인 실패 - 잘못된 비밀번호")
    void login_wrongPassword() {
        authService.register(registerRequest());

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("test@example.com");
        loginReq.setPassword("wrongpassword");

        assertThrows(IllegalArgumentException.class, () ->
                authService.login(loginReq));
    }

    @Test
    @DisplayName("로그인 실패 - 존재하지 않는 이메일")
    void login_emailNotFound() {
        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("nobody@example.com");
        loginReq.setPassword("password123");

        assertThrows(IllegalArgumentException.class, () ->
                authService.login(loginReq));
    }
}
