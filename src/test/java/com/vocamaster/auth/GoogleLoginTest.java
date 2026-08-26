package com.vocamaster.auth;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.auth.dto.LoginRequest;
import com.vocamaster.auth.dto.TokenPair;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 구글 로그인 다리 (ADR-047) — OAuth 왕복 자체는 구글 몫이고,
 * 우리가 책임지는 건 "성공 후 계정 연결·생성·토큰 발급" 3갈래다.
 */
class GoogleLoginTest extends AbstractIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("처음 온 구글 사용자 — 계정 자동 생성(비번 없음, provider=google) + 토큰 발급")
    void firstGoogleLogin_createsUser() {
        String email = "goo_" + System.nanoTime() + "@gmail.com";

        TokenPair pair = authService.loginWithGoogle(email, "현동", "test-ua", "127.0.0.1");

        assertNotNull(pair.accessToken());
        assertNotNull(pair.refreshToken());
        User created = userRepository.findByEmail(email).orElseThrow();
        assertNull(created.getPassword(), "구글 가입자는 비밀번호가 없어야");
        assertEquals("google", created.getProvider());
        assertEquals("현동", created.getNickname());
    }

    @Test
    @DisplayName("같은 이메일의 기존(이메일 가입) 사용자 — 새 계정이 아니라 그 계정으로 로그인 (자동 연결)")
    void existingLocalUser_sameAccount() {
        String email = "link_" + System.nanoTime() + "@gmail.com";
        User local = userRepository.save(User.builder()
                .email(email).password(passwordEncoder.encode("Passw0rd!")).nickname("원주인").build());

        authService.loginWithGoogle(email, "구글이름", "test-ua", "127.0.0.1");

        assertEquals(1, userRepository.findByEmail(email).stream().count(), "계정이 늘어나면 안 됨");
        User after = userRepository.findByEmail(email).orElseThrow();
        assertEquals(local.getId(), after.getId());
        assertEquals("원주인", after.getNickname(), "기존 프로필을 덮어쓰지 않아야");
        assertNotNull(after.getPassword(), "기존 비밀번호 로그인도 계속 가능해야");
    }

    @Test
    @DisplayName("구글 가입 계정에 이메일+비번 로그인 시도 — NPE가 아니라 친절한 400")
    void passwordLoginOnGoogleAccount_rejected() {
        String email = "goonly_" + System.nanoTime() + "@gmail.com";
        authService.loginWithGoogle(email, "구글단독", "test-ua", "127.0.0.1");

        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword("아무비번123");
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> authService.login(req, "test-ua", "127.0.0.1"));
        assertTrue(e.getMessage().contains("구글"), "어느 버튼을 눌러야 하는지 안내해야");
    }
}
