package com.vocamaster.auth;

import com.vocamaster.auth.dto.LoginRequest;
import com.vocamaster.auth.dto.RegisterRequest;
import com.vocamaster.auth.dto.TokenPair;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.common.exception.UnauthorizedException;
import com.vocamaster.user.UserRepository;
import com.vocamaster.user.UserService;
import com.vocamaster.user.User;
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

    private static final String UA = "test-agent";
    private static final String IP = "127.0.0.1";

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserService userService;

    private RegisterRequest registerRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("test@example.com");
        req.setPassword("password123");
        req.setNickname("tester");
        return req;
    }

    @Test
    @DisplayName("회원가입 성공 - 토큰 쌍 반환")
    void register_success() {
        TokenPair pair = authService.register(registerRequest(), UA, IP);

        assertNotNull(pair.accessToken());
        assertNotNull(pair.refreshToken());
        assertTrue(jwtProvider.validate(pair.accessToken()));
        assertTrue(jwtProvider.validate(pair.refreshToken()));
        assertEquals("access", jwtProvider.getType(pair.accessToken()));
        assertEquals("refresh", jwtProvider.getType(pair.refreshToken()));
        assertTrue(userRepository.existsByEmail("test@example.com"));
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    void register_duplicateEmail() {
        authService.register(registerRequest(), UA, IP);

        assertThrows(BadRequestException.class, () ->
                authService.register(registerRequest(), UA, IP));
    }

    @Test
    @DisplayName("로그인 성공")
    void login_success() {
        authService.register(registerRequest(), UA, IP);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("test@example.com");
        loginReq.setPassword("password123");

        TokenPair pair = authService.login(loginReq, UA, IP);

        assertNotNull(pair.accessToken());
        assertNotNull(pair.refreshToken());
        Long userId = jwtProvider.getUserId(pair.accessToken());
        assertEquals(1L, userRepository.count());
        assertNotNull(userId);
    }

    @Test
    @DisplayName("로그인 실패 - 잘못된 비밀번호")
    void login_wrongPassword() {
        authService.register(registerRequest(), UA, IP);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("test@example.com");
        loginReq.setPassword("wrongpassword");

        assertThrows(UnauthorizedException.class, () ->
                authService.login(loginReq, UA, IP));
    }

    @Test
    @DisplayName("로그인 실패 - 존재하지 않는 이메일")
    void login_emailNotFound() {
        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("nobody@example.com");
        loginReq.setPassword("password123");

        assertThrows(UnauthorizedException.class, () ->
                authService.login(loginReq, UA, IP));
    }

    @Test
    @DisplayName("Refresh Token Rotation - 정상 회전")
    void refresh_rotation_success() {
        TokenPair initial = authService.register(registerRequest(), UA, IP);

        TokenPair rotated = authService.refresh(initial.refreshToken(), UA, IP);

        assertNotNull(rotated.accessToken());
        assertNotNull(rotated.refreshToken());
        assertNotEquals(initial.refreshToken(), rotated.refreshToken(), "회전 후 refresh는 새 값이어야 함");
    }

    @Test
    @DisplayName("Refresh Token Reuse Detection - 폐기된 토큰 재사용 시 401 + 모든 세션 무효화")
    void refresh_reuseDetection() {
        TokenPair initial = authService.register(registerRequest(), UA, IP);

        // 1차 회전 — 정상
        TokenPair rotated = authService.refresh(initial.refreshToken(), UA, IP);

        // 폐기된 옛 refresh로 다시 시도 → 401 + 모든 토큰 폐기
        assertThrows(UnauthorizedException.class, () ->
                authService.refresh(initial.refreshToken(), UA, IP));

        // 회전된 새 토큰조차 사용 불가 (mass logout)
        assertThrows(UnauthorizedException.class, () ->
                authService.refresh(rotated.refreshToken(), UA, IP));
    }

    @Test
    @DisplayName("Refresh - access token으로 시도하면 거부")
    void refresh_withAccessToken_rejected() {
        TokenPair pair = authService.register(registerRequest(), UA, IP);

        assertThrows(UnauthorizedException.class, () ->
                authService.refresh(pair.accessToken(), UA, IP));
    }

    @Test
    @DisplayName("Logout - refresh token 폐기 후 재사용 불가")
    void logout_revokesRefresh() {
        TokenPair pair = authService.register(registerRequest(), UA, IP);

        authService.logout(pair.refreshToken());

        assertThrows(UnauthorizedException.class, () ->
                authService.refresh(pair.refreshToken(), UA, IP));
    }
    @Test
    @DisplayName("회원 탈퇴 - deletedAt 설정 = mass logout = 재로그인 차단")
    void deleteAccount_fullFlow() {
        TokenPair pair = authService.register(registerRequest(), UA, IP);
        Long userId = jwtProvider.getUserId(pair.accessToken());

        //회원탈퇴
        userService.deleteAccount(userId);

        // deletedAt 설정 되었는지
        User user = userRepository.findById(userId).orElseThrow();
        assertTrue(user.isDeleted());

        // mass logout - refresh 사용불가  => mass logout 은 모든 세션에서 로그아웃
        assertThrows(UnauthorizedException.class, () ->
                authService.refresh(pair.refreshToken(), UA, IP));

        // then 3: 같은 이메일로 재로그인 차단
        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("test@example.com");
        loginReq.setPassword("password123");
        assertThrows(UnauthorizedException.class, () ->
                authService.login(loginReq, UA, IP));
    }
}
