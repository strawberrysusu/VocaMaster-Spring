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
    @DisplayName("같은 이메일의 기존(이메일 가입) 계정 — 자동 연결 대신 거부 (pre-hijacking 차단, 정책 변경 8/28)")
    void existingLocalUser_refused() {
        // 시나리오: 공격자가 피해자의 Gmail 주소로 먼저 일반 가입해둔 상태.
        // 진짜 주인이 구글 로그인해도 그 계정에 '합류'시키면 안 된다 — 두 사람이 한 계정을 공유하게 되므로.
        String email = "link_" + System.nanoTime() + "@gmail.com";
        User local = userRepository.save(User.builder()
                .email(email).password(passwordEncoder.encode("Passw0rd!")).nickname("선점자").build());

        assertThrows(LocalAccountExistsException.class,
                () -> authService.loginWithGoogle(email, "진짜주인", "test-ua", "127.0.0.1"));

        // 거부 이후에도 기존 계정은 무변경 (새 계정 생성도, 프로필 덮어쓰기도 없어야)
        assertEquals(1, userRepository.findByEmail(email).stream().count());
        User after = userRepository.findByEmail(email).orElseThrow();
        assertEquals(local.getId(), after.getId());
        assertEquals("선점자", after.getNickname());
    }

    @Test
    @DisplayName("구글로 만든 계정의 재로그인 — 새 계정 없이 같은 계정으로")
    void repeatedGoogleLogin_sameAccount() {
        String email = "re_" + System.nanoTime() + "@gmail.com";
        authService.loginWithGoogle(email, "첫로그인", "test-ua", "127.0.0.1");
        Long firstId = userRepository.findByEmail(email).orElseThrow().getId();

        TokenPair pair = authService.loginWithGoogle(email, "두번째", "test-ua", "127.0.0.1");

        assertNotNull(pair.accessToken());
        assertEquals(firstId, userRepository.findByEmail(email).orElseThrow().getId());
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
