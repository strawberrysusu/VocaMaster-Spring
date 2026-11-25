package com.vocamaster.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 직접 입력한 답의 채점 규칙 — 타이핑 모드(서버)와 듣기 모드(프론트)가 같은 규칙을 쓴다 (ADR-051, ADR-026 결정 B 확장).
 *
 * <p>정답 칸 하나에서 "이 중 하나만 맞히면 정답"인 후보를 뽑는다.
 * <ol>
 *   <li><b>예전 규칙 그대로</b> — 쉼표로 나눈 조각. 전에 정답이던 입력은 계속 정답이다(뒤로 호환).</li>
 *   <li><b>설명·표기 기호를 걷어낸 조각</b>
 *     <ul>
 *       <li>대괄호 — 낱말에 붙은 {@code [ ]}는 그 낱말 대신 쓸 수 있는 말, 띄어 쓴 {@code [ ]}는 설명이라 삭제 (ADR-066)</li>
 *       <li>괄호 안 설명 삭제 — {@code ( )} {@code （ ）}, 겹괄호는 안쪽부터</li>
 *       <li>영어 품사 표시 {@code n. v. a. ad.} 등은 구분자로 — {@code "n. 바닥, 아래 v. 근거를 두다"}</li>
 *       <li>{@code ① ②} … 는 구분자로</li>
 *       <li>{@code , ， ; ； / ／ ・} 로 나눔</li>
 *     </ul>
 *   </li>
 * </ol>
 * 비교 전에 입력과 후보 모두: 폭 없는 글자 삭제 → 물결표({@code ~ ～ 〜}) 삭제 → 연속 공백을 하나로 → 앞뒤 공백 제거 → 대소문자 무시.
 *
 * <p><b>2026-11-18 (ADR-062, 사용자 결정 — "띄어쓰기, 뜻을 앞뒤 섞어 적어도 맞게")</b>
 * <ul>
 *   <li>한글·가나·한자가 든 <b>낱개 뜻</b>은 띄어쓰기를 빼고도 비교한다({@code "피할수없는" = "피할 수 없는"}).
 *       영어처럼 로마자뿐인 뜻은 띄어쓰기도 철자라 그대로 본다({@code "app le" ≠ "apple"}) — 구분자로 묶인 답
 *       {@code "ice cream/아이스크림"}에서도 영어 쪽은 그대로다({@code "icecream/아이스크림"}은 오답, Codex 8차 검산 P2).</li>
 *   <li>입력에 뜻을 여러 개 적었으면({@code , ， ; ； / ／ ・} 로 나눔) 조각이 전부 후보일 때 정답 — 순서는 상관없다
 *       ({@code "끈기,고집" ← "n. 고집, 끈기"}). 후보에 없는 뜻이 하나라도 섞이면 오답({@code "사과, 포도" ← "사과, 능금"}).</li>
 *   <li>입력에 적은 괄호 설명·품사 표시·①②도 정답 칸처럼 걷어내고 본다 (같은 날 보완 2, Codex 9차 검산) —
 *       {@code "피할수없는 (불가피한)" ← "피할 수 없는 (불가피한)"}, {@code "a. 피할수없는" ← "a. 피할 수 없는"}.
 *       위 두 규칙으로 안 맞을 때만 쓰는 마지막 단계라, 전에 정답이던 입력이 오답으로 바뀌는 일은 없다.</li>
 * </ul>
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
    private static final String SPACE_CHARS = "\\t\\n\\u000B\\f\\r \\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000";
    static final String SPACE = "[" + SPACE_CHARS + "]";
    /**
     * 대괄호 [ ] (ADR-066, 11/25) — 카드가 쓰는 두 모양 (19,156장 중 3장). 프론트 answerMatch.ts와 똑같이 유지.
     * <ul>
     *   <li>낱말에 바로 붙은 [ ] = 그 낱말 대신 쓸 수 있는 말: {@code "아무도[아무것도] ~않다"} → {@code "아무도 ~않다"} 또는 {@code "아무것도 ~않다"}</li>
     *   <li>띄어 쓴 [ ] = 설명(발음 기호 등) → 지운다: {@code "n. [ˈɪnsens] 향"} → {@code "n. 향"}</li>
     * </ul>
     */
    private static final Pattern ALT_BRACKET =
            Pattern.compile("([^" + SPACE_CHARS + "\\[\\]［］()（）,，;；/／\\u30FB]+)[\\[［]([^\\[\\]［］]+)[\\]］]");
    private static final Pattern NOTE_BRACKET = Pattern.compile("[\\[［][^\\[\\]［］]*[\\]］]");
    /** 한 칸에 '대신 쓸 수 있는 말'이 이보다 많으면 나머지 [ ]는 설명으로 보고 지운다 (경우의 수가 2^n으로 늘지 않게) */
    private static final int MAX_BRACKET_ALTS = 4;
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
    /**
     * 한글(자모·호환 자모·음절)·가나(히라가나·가타카나·반각 가타카나)·한자 —
     * 이 글자가 든 낱개 뜻만 띄어쓰기를 빼고 비교한다 (ADR-062). 프론트 answerMatch.ts의 CJK와 똑같이 유지.
     * 한자 범위는 화면의 시험지 생성기(testSheet.ts KANJI)와 같다: 々〆, 확장 A, 통합, 호환 F900–FAFF, BMP 밖 20000–3FFFF —
     * 호환 한자·확장 한자만으로 된 답도 띄어쓰기를 무시한다 (Codex 8차 검산 P3, 11/18)
     */
    private static final Pattern CJK = Pattern.compile(
            "[\\u1100-\\u11FF\\u3005\\u3006\\u3040-\\u30FF\\u3130-\\u318F\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uAC00-\\uD7A3\\uF900-\\uFAFF\\uFF66-\\uFF9F\\x{20000}-\\x{3FFFF}]");

    private AnswerMatcher() {
    }

    /**
     * 입력이 정답 칸의 후보와 맞으면 true. 빈 입력·공백만은 항상 오답.
     * <ul>
     *   <li>입력 전체가 후보 하나와 같거나</li>
     *   <li>입력을 구분자로 나눈 조각이 전부 후보일 때 (뜻을 여러 개, 순서를 바꿔 적은 답 — ADR-062)</li>
     *   <li>입력에서 괄호 설명·품사 표시·①②를 걷어낸 조각이 전부 후보일 때 (ADR-062 보완 2)</li>
     * </ul>
     */
    public static boolean matches(String typed, String answer) {
        if (typed == null || answer == null) {
            return false;
        }
        String raw = ZERO_WIDTH.matcher(typed).replaceAll("");
        String t = normalize(raw);
        if (t.isEmpty()) {
            return false;
        }
        Set<String> candidates = candidates(answer);
        // 띄어쓰기를 빼고 비교하는 것은 낱개 뜻뿐이다. "ice cream/아이스크림"처럼 구분자로 묶인 예전 후보를 통째로 쓰면
        // 한글이 섞여 있다는 이유로 영어 쪽 띄어쓰기까지 무시돼 "icecream/아이스크림"이 정답이 됐다 (Codex 8차 검산 P2, 11/18)
        Set<String> spaceless = new HashSet<>();
        for (String c : cleanPieces(ZERO_WIDTH.matcher(answer).replaceAll(""))) {
            if (CJK.matcher(c).find()) {
                spaceless.add(c.replace(" ", ""));   // 정규화한 뒤에는 보통 공백(U+0020)만 남는다
            }
        }
        if (hits(t, candidates, spaceless)) {
            return true;
        }
        List<String> pieces = new ArrayList<>();
        for (String piece : SEPARATORS.split(raw)) {
            String p = normalize(piece);
            if (!p.isEmpty()) {
                pieces.add(p);
            }
        }
        if (allHit(pieces, candidates, spaceless)) {
            return true;
        }
        // 입력도 정답 칸과 같은 방법으로 걷어내고 본다 — 카드에 적힌 대로 괄호 설명·품사 표시까지 따라 적은 답
        // ("피할수없는 (불가피한)", "a. 피할수없는"). 괄호 안에만 적은 입력은 조각이 하나도 안 남아 오답이다
        return allHit(cleanPieces(raw), candidates, spaceless);
    }

    /** 조각이 하나 이상 있고 전부 후보와 맞는가 */
    private static boolean allHit(List<String> pieces, Set<String> candidates, Set<String> spaceless) {
        if (pieces.isEmpty()) {
            return false;
        }
        for (String p : pieces) {
            if (!hits(p, candidates, spaceless)) {
                return false;
            }
        }
        return true;
    }

    /** 조각 하나가 후보와 같은가 — 그대로, 또는 (한글·가나·한자가 든 후보에 한해) 띄어쓰기를 뺀 모양으로 */
    private static boolean hits(String piece, Set<String> candidates, Set<String> spaceless) {
        return candidates.contains(piece) || spaceless.contains(piece.replace(" ", ""));
    }

    /** 정답 칸에서 뽑은 후보 (정규화된 값, 중복 없음, 처음 나온 순서) */
    public static Set<String> candidates(String answer) {
        if (answer == null) {
            return Collections.emptySet();
        }
        Set<String> out = new LinkedHashSet<>();
        // 폭 없는 글자는 처음부터 없는 것으로 — normalize()도 지우지만 여기서 먼저 지워야 한다:
        // "n.(제로폭)사과"에서 n. 뒤가 공백인지 보는 품사 판단이 그 글자에 가로막히기 때문
        String a = ZERO_WIDTH.matcher(answer).replaceAll("");
        for (String piece : a.split(",")) {                             // 1) 예전 규칙
            add(out, piece);
        }
        out.addAll(cleanPieces(a));                                     // 2) 설명·표기 기호 걷어내기
        return out;
    }

    /**
     * 정답 칸을 '낱개 뜻'으로 나눈 조각 (정규화된 값, 빈 조각 제외, 나온 순서) —
     * 대괄호를 풀고(ADR-066), 괄호 설명·품사 표시·①②를 걷어내고 모든 구분자로 나눈다. 폭 없는 글자를 먼저 지운 문자열을 받는다.
     */
    static List<String> cleanPieces(String a) {
        List<String> out = new ArrayList<>();
        for (String variant : bracketVariants(a, 0)) {
            String cleaned = stripParentheses(variant);
            cleaned = PART_OF_SPEECH.matcher(cleaned).replaceAll("$1,");
            cleaned = CIRCLED_NUMBER.matcher(cleaned).replaceAll(",");
            for (String piece : SEPARATORS.split(cleaned)) {
                String n = normalize(piece);
                if (!n.isEmpty()) {
                    out.add(n);
                }
            }
        }
        return out;
    }

    /**
     * 대괄호를 푼 모양들 (ADR-066) — 낱말에 붙은 [ ]마다 '앞 낱말 그대로'와 '[ ] 안의 말로 바꾼 것' 두 갈래,
     * 남은 [ ](띄어 쓴 것·너무 많은 것)는 지운다. [ ]가 없으면 받은 문자열 하나.
     */
    static List<String> bracketVariants(String s, int depth) {
        Matcher m = ALT_BRACKET.matcher(s);
        if (depth >= MAX_BRACKET_ALTS || !m.find()) {
            return List.of(NOTE_BRACKET.matcher(s).replaceAll(""));
        }
        String head = s.substring(0, m.start());
        String tail = s.substring(m.end());
        List<String> out = new ArrayList<>(bracketVariants(head + m.group(1) + tail, depth + 1));
        out.addAll(bracketVariants(head + m.group(2) + tail, depth + 1));
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
        // 5) 대소문자 무시 — 대소문자 표는 실행 환경의 유니코드 버전을 따른다(서버 JRE 17 = Unicode 13, 화면 = 브라우저).
        //    Unicode 14 이후 새 글자는 서버·화면이 다를 수 있다 — 단어장 글자(한·일·영)와는 무관 (ADR-051 한계)
        t = t.toLowerCase(Locale.ROOT);
        // 6) 끝 시그마 ς → 보통 시그마 σ — 대문자 Σ를 소문자로 바꿀 때 Java와 JS가 σ/ς를 고르는 규칙이 달라서 하나로 접는다
        return t.replace('ς', 'σ');
    }

    private static void add(Set<String> out, String piece) {
        String n = normalize(piece);
        if (!n.isEmpty()) {
            out.add(n);
        }
    }
}
