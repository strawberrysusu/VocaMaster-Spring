package com.vocamaster.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Bearer access token을 검증하고 SecurityContext에 CustomUserDetails를 박는 필터.
 *
 * - DB 조회 X (JWT 자체 검증 + JWT의 claim만 사용) — 성능 ↑
 * - type=access 만 통과 (refresh token으로 일반 API 호출 차단 — 이중 방어)
 * - 검증 실패 시 SecurityContext 안 박음 → 다운스트림에서 Spring Security가 401 처리
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            if (jwtProvider.validate(token) && "access".equals(jwtProvider.getType(token))) {
                Long userId = jwtProvider.getUserId(token);
                String email = jwtProvider.getEmail(token);
                CustomUserDetails principal = new CustomUserDetails(userId, email);

                var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }
}
