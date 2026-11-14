package com.vocamaster.common;

import java.util.regex.Pattern;

/**
 * 입력 검사에 같이 쓰는 정규식 (@Pattern의 regexp는 컴파일 상수여야 해서 여기 모은다).
 */
public final class ValidationPatterns {

    /**
     * 눈에 보이는 글자가 하나라도 있다 — 단어·뜻·덱 제목의 '빈칸 금지' 규칙 하나 (Codex 검산 2026-11-14).
     * 만들 때(@NotBlank 옆)·수정 때(PATCH)·가져오기 줄(hasText)이 모두 이걸 쓴다 — 예전엔 셋의 기준이 달라
     * 만들 때 통과한 값을 수정 때 거부했고, 공개 덱이면 탐색에 빈 제목이 보였다.
     * <ul>
     *   <li>@Pattern은 null을 통과시킨다 — PATCH의 '안 보낸 칸'은 그대로. 만들 때는 @NotBlank가 null을 막는다</li>
     *   <li>(?s): 줄바꿈이 섞인 값도 한 덩어리로. (?U): \s를 유니코드 공백으로 — 전각 공백(U+3000, 일본어 입력기)·NBSP도 공백</li>
     *   <li>보이지 않는 글자도 빈칸으로: 폭 0 문자 등 서식 문자(\p{Cf} — U+200B·U+FEFF…)와 한글 채움 문자(U+115F·U+1160·U+3164·U+FFA0)</li>
     * </ul>
     */
    public static final String HAS_TEXT = "(?U)(?s).*[^\\s\\p{Cf}\\u115F\\u1160\\u3164\\uFFA0].*";

    private static final Pattern HAS_TEXT_PATTERN = Pattern.compile(HAS_TEXT);

    private ValidationPatterns() {
    }

    /** HAS_TEXT를 코드에서 — 가져오기 줄 검사용. null은 빈칸 */
    public static boolean hasText(String s) {
        return s != null && HAS_TEXT_PATTERN.matcher(s).matches();
    }
}
