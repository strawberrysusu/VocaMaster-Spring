package com.vocamaster.auth;

import com.vocamaster.auth.dto.TokenPair;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/**
 * 구글 인증 성공 → 우리 JWT 공장으로 연결하는 다리 (ADR-047).
 *
 * 세션 기반(oauth2Login 기본형)이라면 여기서 할 일이 없지만, 우리 API는 STATELESS로
 * Bearer JWT만 인정한다 — 구글 로그인이 성공해도 우리 토큰이 없으면 모든 API가 401.
 * 그래서 성공 순간 기존 발급 경로(issueTokens)로 refresh 쿠키를 심고 SPA로 보낸다.
 * SPA(/app/login?oauth=success)는 그 쿠키로 /auth/refresh를 호출해 access token을 얻는다.
 * 쿠키 속성은 AuthController와 동일해야 refresh가 정상 동작한다.
 */
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;

    @Value("${auth.cookie.secure}")
    private boolean cookieSecure;

    @Value("${auth.cookie.same-site}")
    private String cookieSameSite;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        try {
            OAuth2User principal = (OAuth2User) authentication.getPrincipal();
            String email = principal.getAttribute("email");
            Boolean verified = principal.getAttribute("email_verified");
            // 미검증 이메일로 남의 계정에 자동 연결되는 사고 방지 — 구글의 검증 도장 필수
            if (email == null || !Boolean.TRUE.equals(verified)) {
                response.sendRedirect("/app/login?oauth=error");
                return;
            }
            TokenPair pair = authService.loginWithGoogle(
                    email, principal.getAttribute("name"), userAgent(request), ip(request));

            ResponseCookie cookie = ResponseCookie.from("refresh_token", pair.refreshToken())
                    .httpOnly(true)
                    .secure(cookieSecure)
                    .sameSite(cookieSameSite)
                    .path("/auth")
                    .maxAge(Duration.ofDays(14))
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
            response.sendRedirect("/app/login?oauth=success");
        } catch (LocalAccountExistsException e) {
            // pre-hijack 가드 (Codex 검산 8/28) — 일반 가입 계정과 자동 연결 안 함, 어느 문으로 들어올지 안내
            response.sendRedirect("/app/login?oauth=local_exists");
        } catch (Exception e) {
            // 어떤 실패든 스택이 사용자 화면에 새지 않게 — 로그인 화면에서 안내
            response.sendRedirect("/app/login?oauth=error");
        }
    }

    private String userAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) return null;
        return ua.length() > 255 ? ua.substring(0, 255) : ua;
    }

    private String ip(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
