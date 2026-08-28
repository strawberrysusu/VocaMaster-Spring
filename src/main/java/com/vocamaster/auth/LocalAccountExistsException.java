package com.vocamaster.auth;

/**
 * 구글 로그인 시 같은 이메일의 일반(local) 가입 계정이 이미 있을 때 — 자동 연결 대신 거부 (Codex 검산 2026-08-28).
 *
 * 이메일 가입은 소유 검증이 없으므로, 공격자가 남의 Gmail 주소로 먼저 가입해두면
 * 진짜 주인이 구글 로그인했을 때 그 계정에 '합류'하게 되는 pre-hijacking 경로가 생긴다.
 * email_verified는 "구글 사용자가 그 이메일의 주인"만 증명할 뿐,
 * 기존 local 계정을 누가 만들었는지는 증명하지 못한다.
 *
 * OAuth2SuccessHandler가 이 예외를 잡아 /app/login?oauth=local_exists 로 안내한다.
 */
public class LocalAccountExistsException extends RuntimeException {
    public LocalAccountExistsException() {
        super("같은 이메일의 일반 가입 계정이 있어 구글 자동 연결을 거부했습니다");
    }
}
