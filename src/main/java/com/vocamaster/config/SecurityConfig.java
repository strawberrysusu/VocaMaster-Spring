package com.vocamaster.config;

import com.vocamaster.auth.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/auth/register", "/auth/login", "/auth/refresh", "/auth/logout",
                    "/swagger-ui/**", "/v3/api-docs/**", "/api-docs/**",
                    "/pages/**", "/css/**", "/js/**",
                    "/", "/app/**"                              // React SPA (정적 번들 + 딥링크 fallback)
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

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
