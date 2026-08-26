package com.vocamaster.config;

import com.vocamaster.auth.JwtAuthFilter;
import com.vocamaster.auth.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    // 비어 있으면 구글 로그인 자체가 꺼짐 (로컬 dev 기본·테스트) — yml의 google.* 가 환경변수를 다리 놓는다 (ADR-047)
    @Value("${google.client-id:}")
    private String googleClientId;

    @Value("${google.client-secret:}")
    private String googleClientSecret;

    // OAuth2SuccessHandler는 생성자가 아니라 여기(메서드 파라미터)로 받는다 —
    // 생성자로 받으면 AuthService→PasswordEncoder(이 클래스)→핸들러→AuthService 순환 참조로 부팅 실패
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, OAuth2SuccessHandler oAuth2SuccessHandler) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/auth/register", "/auth/login", "/auth/refresh", "/auth/logout",
                    "/oauth2/**", "/login/oauth2/**",           // 구글 로그인 시작·콜백 (ADR-047)
                    "/swagger-ui/**", "/v3/api-docs/**", "/api-docs/**",
                    "/pages/**", "/css/**", "/js/**",
                    "/", "/app/**",                             // React SPA (정적 번들 + 딥링크 fallback)
                    "/privacy.html"                             // 개인정보처리방침 — 구글 OAuth 게시 요건 (ADR-047)
                ).permitAll()
                // 공개 표면은 '조회'만 익명 — 쓰기(좋아요 등)는 로그인 필수 (ADR-032)
                .requestMatchers(HttpMethod.GET, "/public/**").permitAll()
                .anyRequest().authenticated()
            )
            // 미인증/만료 토큰 = 401 (권한 부족 403과 분리) — 프런트 자동 갱신 인터셉터가 401을 신호로 씀.
            // entry point 미설정 시 스프링 기본이 403이라 만료 토큰에서 갱신이 영영 안 돌던 문제 수리 (Codex 검산)
            .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"status\":401,\"code\":\"UNAUTHORIZED\",\"message\":\"로그인이 필요합니다\"}");
            }))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        // 구글 로그인 (ADR-047) — 키가 없으면 이 블록이 아예 안 붙어 기존과 완전 동일하게 동작.
        // 성공 시 OAuth2SuccessHandler가 우리 JWT 발급으로 연결 (세션은 인증 왕복 중 state 보관에만 쓰임)
        if (!googleClientId.isBlank()) {
            http.oauth2Login(oauth -> oauth
                .clientRegistrationRepository(new InMemoryClientRegistrationRepository(
                    CommonOAuth2Provider.GOOGLE.getBuilder("google")
                        .clientId(googleClientId)
                        .clientSecret(googleClientSecret)
                        .build()))
                .successHandler(oAuth2SuccessHandler)
                .failureHandler((req, res, ex) -> res.sendRedirect("/app/login?oauth=error")));
        }

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
