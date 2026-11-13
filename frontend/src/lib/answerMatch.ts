// 직접 입력한 답의 채점 규칙 — 서버 com.vocamaster.common.AnswerMatcher 와 같은 규칙 (ADR-051).
// 두 구현은 src/test/resources/answer-match-cases.json 의 같은 사례로 검증한다
// (서버: AnswerMatcherTest, 프론트: npm test → scripts/test-answer-match.mjs).
//
// 정답 칸 하나에서 "이 중 하나만 맞히면 정답"인 후보를 뽑는다.
//  1) 예전 규칙 그대로 — 쉼표로 나눈 조각 (전에 정답이던 입력은 계속 정답)
//  2) 설명·표기 기호를 걷어낸 조각 — 괄호 설명 삭제, 영어 품사 표시(n. v. a. ad. …)와 ①②는 구분자,
//     , ， ; ； / ／ ・ 로 나눔
// 비교 전에: 물결표(~ ～ 〜) 삭제 → 연속 공백 하나로 → 앞뒤 공백 제거 → 대소문자 무시.
// 단어 중간 띄어쓰기는 그대로 본다("app le" ≠ "apple"). 쉼표로 묶인 답 전체를 통째로 친 것은 후보가 아니다.
// 정규식에 뒤보기(lookbehind)를 쓰지 않는다 — 오래된 iOS Safari에서 파일 전체가 깨진다.

const INNER_PAREN = /[(（][^()（）]*[)）]/g
const PART_OF_SPEECH = /(^|[^A-Za-z])(?:n|v|a|ad|adv|adj|prep|conj|pron|int|phr|pl)\.(?=[ \t\n\r\f\v 　]|$)/g
const CIRCLED_NUMBER = /[①-⑳]/g
const SEPARATORS = /[,，;；/／・]/
const TILDE = /[~～〜]/g
// 서버(Java)와 같은 집합이 되도록 \s 대신 명시
const SPACES = /[ \t\n\r\f\v 　]+/g

export function normalizeAnswer(s: string): string {
  return s.replace(TILDE, '').replace(SPACES, ' ').trim().toLowerCase()
}

function stripParentheses(s: string): string {
  let cur = s
  let prev: string
  do {
    prev = cur
    cur = cur.replace(INNER_PAREN, '')
  } while (cur !== prev)
  return cur
}

/** 정답 칸에서 뽑은 후보 (정규화된 값) */
export function answerCandidates(answer: string | null | undefined): Set<string> {
  const out = new Set<string>()
  if (answer == null) return out
  const add = (piece: string) => {
    const n = normalizeAnswer(piece)
    if (n) out.add(n)
  }
  answer.split(',').forEach(add) // 1) 예전 규칙
  const cleaned = stripParentheses(answer) // 2) 설명·표기 기호 걷어내기
    .replace(PART_OF_SPEECH, '$1,')
    .replace(CIRCLED_NUMBER, ',')
  cleaned.split(SEPARATORS).forEach(add)
  return out
}

/** 입력이 정답 칸들 중 어느 하나의 후보와 같으면 true. 빈 입력·공백만은 항상 오답. */
export function matchesAnswer(typed: string | null | undefined, ...answers: Array<string | null | undefined>): boolean {
  const t = normalizeAnswer(typed ?? '')
  if (!t) return false
  return answers.some((a) => answerCandidates(a).has(t))
}
