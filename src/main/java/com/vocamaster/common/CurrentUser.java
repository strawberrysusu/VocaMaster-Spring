package com.vocamaster.common;

import com.vocamaster.auth.CustomUserDetails;
import com.vocamaster.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 현재 로그인한 사용자 정보를 SecurityContext에서 꺼내는 유틸.
 * @AuthenticationPrincipal CustomUserDetails 패턴이 더 표준이지만,
 * 기존 호출처 호환을 위해 유지.
 */
public class CurrentUser {

    public static CustomUserDetails get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails cud)) {
            throw new UnauthorizedException("인증되지 않은 요청입니다");
        }
        return cud;
    }

    public static Long getId() {
        return get().getUserId();
    }

    /** 익명 허용 엔드포인트용 — 로그인 안 했으면 null (예외 X). 공개 목록의 likedByMe/isMine 계산에 사용 */
    public static Long tryGetId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails cud)) {
            return null;
        }
        return cud.getUserId();
    }

    public static String getEmail() {
        return get().getEmail();
    }
}
