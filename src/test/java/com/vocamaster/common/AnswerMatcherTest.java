package com.vocamaster.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 채점 규칙 단위 테스트 (스프링 없이). 사례 파일은 프론트 {@code frontend/scripts/test-answer-match.mjs} 와 공유한다
 * — 두 구현이 같은 표를 통과해야 타이핑(서버)과 듣기(프론트)가 같은 답을 같게 채점한다.
 */
class AnswerMatcherTest {

    @TestFactory
    @DisplayName("공유 사례표 (answer-match-cases.json)")
    List<DynamicTest> sharedCases() throws IOException {
        JsonNode cases;
        try (InputStream in = getClass().getResourceAsStream("/answer-match-cases.json")) {
            assertNotNull(in, "src/test/resources/answer-match-cases.json 이 있어야 한다");
            cases = new ObjectMapper().readTree(in);
        }
        assertTrue(cases.size() >= 40, "사례가 통째로 빠지지 않았는지");
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : cases) {
            String typed = c.get("typed").asText();
            String answer = c.get("answer").asText();
            boolean expected = c.get("expected").asBoolean();
            String why = c.get("why").asText();
            tests.add(DynamicTest.dynamicTest(
                    (expected ? "O " : "X ") + "[" + typed + "] ← " + answer,
                    () -> assertEquals(expected, AnswerMatcher.matches(typed, answer), why)));
        }
        return tests;
    }

    @Test
    @DisplayName("null 입력·null 정답은 오답이고 예외가 나지 않는다")
    void nullSafe() {
        assertFalse(AnswerMatcher.matches(null, "사과"));
        assertFalse(AnswerMatcher.matches("사과", null));
        assertTrue(AnswerMatcher.candidates(null).isEmpty());
    }

    @Test
    @DisplayName("후보는 예전 쉼표 조각을 모두 포함한다 — 전에 맞던 답이 틀려지지 않는다")
    void candidatesKeepLegacyPieces() {
        String answer = "n. 바닥, 아래 v. 근거를 두다";
        Set<String> c = AnswerMatcher.candidates(answer);
        assertTrue(c.contains("n. 바닥"), "예전 후보 1");
        assertTrue(c.contains("아래 v. 근거를 두다"), "예전 후보 2");
        assertTrue(c.containsAll(List.of("바닥", "아래", "근거를 두다")), "새 후보");
    }

    @Test
    @DisplayName("괄호만 있는 뜻이라도 후보가 비지 않는다 (예전 조각이 남는다)")
    void parenthesesOnlyAnswerStillHasCandidate() {
        assertTrue(AnswerMatcher.matches("(the uninitiated)", "(the uninitiated)"));
        assertFalse(AnswerMatcher.candidates("(the uninitiated)").isEmpty());
    }

    @Test
    @DisplayName("닫히지 않은 괄호는 지우지 않는다")
    void unbalancedParenthesisKept() {
        assertTrue(AnswerMatcher.matches("고작(최대 한도", "고작(최대 한도"));
    }
}
