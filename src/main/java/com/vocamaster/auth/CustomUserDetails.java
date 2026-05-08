package com.vocamaster.auth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security가 인증된 사용자를 나타낼 때 쓰는 표준 객체.
 * JWT에서 추출한 최소 정보(userId, email)만 보유 — 매 요청마다 DB 조회 X.
 *
 * Controller에서 @AuthenticationPrincipal CustomUserDetails 로 받기.
 */
@Getter
@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String email;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return null;  // JWT 인증이라 password 의미 없음
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }
}
