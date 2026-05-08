package com.vocamaster.auth;

import com.vocamaster.auth.dto.LoginRequest;
import com.vocamaster.auth.dto.RegisterRequest;
import com.vocamaster.auth.dto.TokenPair;
import com.vocamaster.auth.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@Tag(name = "Auth - 인증")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/auth";  // /auth/* 호출 시에만 cookie 첨부
    private static final Duration REFRESH_COOKIE_MAX_AGE = Duration.ofDays(14);

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "회원가입 (자동 로그인 — access body + refresh httpOnly cookie)")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest req,
                                                  HttpServletRequest request) {
        TokenPair pair = authService.register(req, getUserAgent(request), getIp(request));
        return tokenResponseWithCookie(pair);
    }

    @PostMapping("/login")
    @Operation(summary = "로그인 — access body + refresh httpOnly cookie")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req,
                                               HttpServletRequest request) {
        TokenPair pair = authService.login(req, getUserAgent(request), getIp(request));
        return tokenResponseWithCookie(pair);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Access Token 갱신 (rotation + reuse detection)")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest request) {
        TokenPair pair = authService.refresh(refreshToken, getUserAgent(request), getIp(request));
        return tokenResponseWithCookie(pair);
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃 — refresh token 폐기 + cookie 만료")
    public ResponseEntity<Void> logout(
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        ResponseCookie clear = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)        // dev: false. prod 분기는 다음 단계에서.
                .sameSite("Lax")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clear.toString())
                .build();
    }

    // === helpers ===

    private ResponseEntity<TokenResponse> tokenResponseWithCookie(TokenPair pair) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, pair.refreshToken())
                .httpOnly(true)         // JS에서 못 읽음 (XSS 방어)
                .secure(false)          // dev: false. prod 분기는 다음 단계에서.
                .sameSite("Lax")        // dev: Lax. prod: Strict.
                .path(REFRESH_COOKIE_PATH)
                .maxAge(REFRESH_COOKIE_MAX_AGE)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new TokenResponse(pair.accessToken()));
    }

    private String getUserAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) return null;
        return ua.length() > 255 ? ua.substring(0, 255) : ua;
    }

    private String getIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
