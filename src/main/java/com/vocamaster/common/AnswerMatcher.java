package com.vocamaster.common;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 직접 입력한 답의 채점 규칙 — 타이핑 모드(서버)와 듣기 모드(프론트)가 같은 규칙을 쓴다 (ADR-051, ADR-026 결정 B 확장).
 *
 * <p>정답 칸 하나에서 "이 중 하나만 맞히면 정답"인 후보를 뽑는다.
 * <ol>
 *   <li><b>예전 규칙 그대로</b> — 쉼표로 나눈 조각. 전에 정답이던 입력은 계속 정답이다(뒤로 호환).</li>
 *   <li><b>설명·표기 기호를 걷어낸 조각</b>
 *     <ul>
 *       <li>괄호 안 설명 삭제 — {@code ( )} {@code （ ）}, 겹괄호는 안쪽부터</li>
 *       <li>영어 품사 표시 {@code n. v. a. ad.} 등은 구분자로 — {@code "n. 바닥, 아래 v. 근거를 두다"}</li>
 *       <li>{@code ① ②} … 는 구분자로</li>
 *       <li>{@code , ， ; ； / ／ ・} 로 나눔</li>
 *     </ul>
 *   </li>
 * </ol>
 * 비교 전에 입력과 후보 모두: 폭 없는 글자 삭제 → 물결표({@code ~ ～ 〜}) 삭제 → 연속 공백을 하나로 → 앞뒤 공백 제거 → 대소문자 무시.
 * 단어 중간 띄어쓰기는 그대로 본다({@code "app le" ≠ "apple"}). 쉼표로 묶인 답 전체를 통째로 친 것은 후보가 아니다.
 *
 * <p>공백은 {@link #SPACE} 한 곳에 적은 글자만이다 — {@code trim()}·{@code \s}를 쓰지 않는다.
 * Java의 trim()은 U+0020 이하만, JS의 trim()은 유니코드 공백까지 지워서 앞뒤에 얇은 공백(U+2009) 같은 게 붙으면
 * 서버(타이핑)와 화면(듣기·온라인 시험)의 채점이 갈렸다 (11/14 검토).
 *
 * <p>프론트 {@code frontend/src/lib/answerMatch.ts} 가 같은 규칙이고, 두 구현 모두
 * {@code src/test/resources/answer-match-cases.json} 의 같은 사례로 검증한다.
 */
public final class AnswerMatcher {

    /**
     * 공백으로 보는 글자 (프론트 answerMatch.ts의 SPACE와 똑같이 유지) —
     * 보통 공백·탭·줄바꿈 계열(TAB LF VT FF CR), NEL(U+0085), NBSP(U+00A0), 오검 공백(U+1680),
     * 여러 폭의 공백(U+2000~U+200A: 얇은 공백 U+2009 포함), 줄·문단 구분자(U+2028·U+2029),
     * 좁은 NBSP(U+202F), 수학 공백(U+205F), 전각 공백(U+3000)
     */
    static final String SPACE = "[\\t\\n\\u000B\\f\\r \\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]";
    /** 폭이 없는 글자(제로폭 공백·제로폭 결합자·단어 결합자·BOM) — 복사해 붙인 글에 숨어 들어와도 없는 것으로 본다 */
    private static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200B\\u200C\\u200D\\u2060\\uFEFF]");
    /** 가장 안쪽 괄호 한 쌍 — 반복 적용하면 겹괄호도 바깥까지 지워진다 */
    private static final Pattern INNER_PAREN = Pattern.compile("[(（][^()（）]*[)）]");
    /**
     * 영어 품사 표시. 앞 글자가 영문자가 아니고 뒤가 공백·끝일 때만 (play a role의 a 같은 낱말은 안 건드림).
     * 끝은 $ 대신 \z — Java의 $는 끝에 줄바꿈 글자가 있으면 그 앞에서도 맞아 JS와 달라진다
     */
    private static final Pattern PART_OF_SPEECH =
            Pattern.compile("(^|[^A-Za-z])(?:n|v|a|ad|adv|adj|prep|conj|pron|int|phr|pl)\\.(?=" + SPACE + "|\\z)");
    /** ① ~ ⑳ */
    private static final Pattern CIRCLED_NUMBER = Pattern.compile("[\\u2460-\\u2473]");
    private static final Pattern SEPARATORS = Pattern.compile("[,，;；/／\\u30FB]");
    private static final Pattern TILDE = Pattern.compile("[~～〜]");
    private static final Pattern SPACES = Pattern.compile(SPACE + "+");
    /** 맨 앞·맨 뒤의 공백 하나 (연속 공백은 이미 하나로 줄어 있다). trim() 대신 — 지우는 글자가 JS와 같도록 */
    private static final Pattern EDGE_SPACE = Pattern.compile("^ | \\z");

    private AnswerMatcher() {
    }

    /** 입력이 정답 칸의 후보 중 하나와 같으면 true. 빈 입력·공백만은 항상 오답. */
    public static boolean matches(String typed, String answer) {
        if (typed == null || answer == null) {
            return false;
        }
        String t = normalize(typed);
        if (t.isEmpty()) {
            return false;
        }
        return candidates(answer).contains(t);
    }

    /** 정답 칸에서 뽑은 후보 (정규화된 값, 중복 없음, 처음 나온 순서) */
    public static Set<String> candidates(String answer) {
        if (answer == null) {
            return Collections.emptySet();
        }
        Set<String> out = new LinkedHashSet<>();
        String a = ZERO_WIDTH.matcher(answer).replaceAll("");           // 폭 없는 글자는 처음부터 없는 것으로
        for (String piece : a.split(",")) {                             // 1) 예전 규칙
            add(out, piece);
        }
        String cleaned = stripParentheses(a);                           // 2) 설명·표기 기호 걷어내기
        cleaned = PART_OF_SPEECH.matcher(cleaned).replaceAll("$1,");
        cleaned = CIRCLED_NUMBER.matcher(cleaned).replaceAll(",");
        for (String piece : SEPARATORS.split(cleaned)) {
            add(out, piece);
        }
        return out;
    }

    static String stripParentheses(String s) {
        String prev;
        String cur = s;
        do {
            prev = cur;
            cur = INNER_PAREN.matcher(cur).replaceAll("");
        } while (!cur.equals(prev));
        return cur;
    }

    static String normalize(String s) {
        String t = ZERO_WIDTH.matcher(s).replaceAll("");   // 1) 폭 없는 글자 삭제
        t = TILDE.matcher(t).replaceAll("");               // 2) 물결표 삭제
        t = SPACES.matcher(t).replaceAll(" ");             // 3) 공백 여러 개(어떤 종류든) → 보통 공백 하나
        t = EDGE_SPACE.matcher(t).replaceAll("");          // 4) 앞뒤 공백 제거
        return t.toLowerCase(Locale.ROOT);                 // 5) 대소문자 무시
    }

    private static void add(Set<String> out, String piece) {
        String n = normalize(piece);
        if (!n.isEmpty()) {
            out.add(n);
        }
    }
}
