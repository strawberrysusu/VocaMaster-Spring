package com.vocamaster.common;

/**
 * 입력 검사에 같이 쓰는 정규식 (@Pattern의 regexp는 컴파일 상수여야 해서 여기 모은다).
 */
public final class ValidationPatterns {

    /**
     * 공백이 아닌 글자가 하나라도 있다 — PATCH에서 '보낸 칸은 비우지 말 것' (Codex 검산 2026-11-14).
     * PATCH는 안 보낸 칸(null)을 그대로 두니 @NotBlank(null도 거부)는 못 쓴다 — @Pattern은 null을 통과시킨다.
     * (?s): 줄바꿈이 섞인 값도 한 덩어리로 본다. (?U): \S의 공백을 유니코드 기준으로 — 전각 공백(U+3000, 일본어 입력기)·NBSP만 있는 값도 빈칸
     */
    public static final String HAS_TEXT = "(?U)(?s).*\\S.*";

    private ValidationPatterns() {
    }
}
