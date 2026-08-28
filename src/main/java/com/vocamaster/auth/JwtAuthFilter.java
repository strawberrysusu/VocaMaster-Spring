package com.vocamaster.auth;

import com.vocamaster.user.UserRepository;
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
 * - JWT 자체 검증 + claim 사용. 단 하나의 DB 조회: 탈퇴 여부(PK 존재 확인) — 탈퇴 즉시
 *   기존 access token(최대 1h 잔존)도 차단하기 위함 (Codex 검산 2026-08-28, privacy 약속 정합)
 * - type=access 만 통과 (refresh token으로 일반 API 호출 차단 — 이중 방어)
 * - 3분기 (2026-08-19 정리):
 *   · 헤더 없음        → 익명으로 통과 (permitAll 공개 GET은 익명 응답, 보호 API는 entry point가 401)
 *   · 헤더 있고 유효   → SecurityContext에 principal
 *   · 헤더 있고 무효   → 필터에서 즉시 401 (익명으로 흘리면 공개 GET에서 개인화가 조용히 풀림)
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            if (jwtProvider.validate(token) && "access".equals(jwtProvider.getType(token))
                    && userRepository.existsByIdAndDeletedAtIsNull(jwtProvider.getUserId(token))) {
                Long userId = jwtProvider.getUserId(token);
                String email = jwtProvider.getEmail(token);
                CustomUserDetails principal = new CustomUserDetails(userId, email);

                var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                // 토큰을 '보냈는데' 만료·위조면 익명으로 흘려보내지 않고 즉시 401.
                // 흘려보내면 permitAll인 공개 GET이 익명으로 통과해 likedByMe/mine이 전부 false가 되고,
                // 401이 아니라 프런트 자동 갱신도 안 돌아 "로그인했는데 하트가 다 꺼진" 상태가 됨 (Codex 검산).
                // 헤더가 아예 없는 요청은 지금처럼 익명 — 그건 정상 사용자
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"status\":401,\"code\":\"UNAUTHORIZED\",\"message\":\"토큰이 만료되었거나 유효하지 않습니다\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
