package com.vocamaster.auth;


import com.vocamaster.auth.dto.RegisterRequest;
import com.vocamaster.auth.dto.TokenPair;
import com.vocamaster.common.exception.UnauthorizedException;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = "jwt.refresh-expiration=1")
public class ExpiredRefreshTest {

    @Autowired
    private AuthService authService;

    @Test
    @DisplayName("Refresh - 만료된 토큰은 거부")
    void refresh_expired_rejected() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("expire@example.com");
        req.setPassword("password123");
        req.setNickname("expirer");


        TokenPair pair = authService.register(req, "test-ua", "127.0.0.1");

        // refresh-expiration=1ms로 발급된 토큰은 sleep 후 확실히 만료
        Thread.sleep(50);

        assertThrows(UnauthorizedException.class, () ->
                authService.refresh(pair.refreshToken(), "test-ua", "127.0.0.1"));
    }
}
