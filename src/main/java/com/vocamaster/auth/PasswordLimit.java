package com.vocamaster.auth;

import com.vocamaster.common.exception.BadRequestException;

import java.nio.charset.StandardCharsets;

/**
 * BCrypt가 받는 비밀번호 길이 한계 — UTF-8로 72바이트 (Codex 검산 2026-11-14).
 *
 * <p>넘기면 BCryptPasswordEncoder.encode()가 IllegalArgumentException을 던져 가입·비밀번호 변경이 500이 됐다 → 미리 400으로 막는다.
 * 글자 수가 아니라 바이트라서 영문·숫자는 72자, 한글·일본어·중국어는 24자까지다(1자 = 3바이트).</p>
 *
 * <p>로그인은 일부러 막지 않는다. 8/25 전(Spring Security 6.3.0)에는 encode()가 72바이트 넘는 비밀번호를 말없이 앞 72바이트로 잘라 저장했다 —
 * 그때 긴 비밀번호로 가입한 사람은 지금도 그 비밀번호 그대로 로그인한다(matches()는 넘는 값도 앞 72바이트로 비교, 예외 없음).
 * 로그인에 길이 제한을 걸면 그 사람들이 못 들어온다. 틀린 긴 비밀번호는 그냥 401 (ApiInputBoundaryTest).</p>
 */
public final class PasswordLimit {

    public static final int MAX_BYTES = 72;

    private PasswordLimit() {
    }

    /** 72바이트를 넘으면 400 */
    public static void check(String rawPassword) {
        if (rawPassword != null && rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new BadRequestException("비밀번호가 너무 깁니다 (영문 72자, 한글 24자까지)");
        }
    }
}
