// 직접 입력한 답의 채점 규칙 — 서버 com.vocamaster.common.AnswerMatcher 와 같은 규칙 (ADR-051).
// 두 구현은 src/test/resources/answer-match-cases.json 의 같은 사례로 검증한다
// (서버: AnswerMatcherTest, 프론트: npm test → scripts/test-answer-match.mjs).
//
// 정답 칸 하나에서 "이 중 하나만 맞히면 정답"인 후보를 뽑는다.
//  1) 예전 규칙 그대로 — 쉼표로 나눈 조각 (전에 정답이던 입력은 계속 정답)
//  2) 설명·표기 기호를 걷어낸 조각 — 괄호 설명 삭제, 영어 품사 표시(n. v. a. ad. …)와 ①②는 구분자,
//     , ， ; ； / ／ ・ 로 나눔
// 비교 전에: 폭 없는 글자 삭제 → 물결표(~ ～ 〜) 삭제 → 연속 공백 하나로 → 앞뒤 공백 제거 → 대소문자 무시.
// 단어 중간 띄어쓰기는 그대로 본다("app le" ≠ "apple"). 쉼표로 묶인 답 전체를 통째로 친 것은 후보가 아니다.
// 정규식에 뒤보기(lookbehind)를 쓰지 않는다 — 오래된 iOS Safari에서 파일 전체가 깨진다.
//
// 공백은 SPACE 한 곳에 적은 글자만이다 — trim()·\s를 쓰지 않는다. Java의 trim()은 U+0020 이하만,
// JS의 trim()은 유니코드 공백까지 지워서, 앞뒤에 얇은 공백(U+2009) 같은 게 붙으면 서버(타이핑)와
// 화면(듣기·온라인 시험)의 채점이 갈렸다 (11/14 검토).

// 공백으로 보는 글자 (서버 AnswerMatcher.SPACE와 똑같이 유지) — 탭·줄바꿈 계열, 보통 공백, NEL(U+0085),
// NBSP(U+00A0), 오검 공백(U+1680), 여러 폭의 공백(U+2000~U+200A), 줄·문단 구분자(U+2028·2029),
// 좁은 NBSP(U+202F), 수학 공백(U+205F), 전각 공백(U+3000)
const SPACE = '[\\t\\n\\v\\f\\r \\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]'
// 폭이 없는 글자(제로폭 공백·제로폭 결합자·단어 결합자·BOM) — 복사해 붙인 글에 숨어 들어와도 없는 것으로 본다
// eslint-disable-next-line no-misleading-character-class -- 폭 없는 글자를 한 글자씩 지우려는 것 (이모지처럼 이어 붙인 글자가 아님)
const ZERO_WIDTH = /[\u200B\u200C\u200D\u2060\uFEFF]/g
const INNER_PAREN = /[(（][^()（）]*[)）]/g
const PART_OF_SPEECH = new RegExp('(^|[^A-Za-z])(?:n|v|a|ad|adv|adj|prep|conj|pron|int|phr|pl)\\.(?=' + SPACE + '|$)', 'g')
const CIRCLED_NUMBER = /[①-⑳]/g
const SEPARATORS = /[,，;；/／・]/
const TILDE = /[~～〜]/g
const SPACES = new RegExp(SPACE + '+', 'g')
// 맨 앞·맨 뒤의 공백 하나 (연속 공백은 이미 하나로 줄어 있다). trim() 대신 — 지우는 글자가 서버와 같도록
const EDGE_SPACE = /^ | $/g

export function normalizeAnswer(s: string): string {
  return s
    .replace(ZERO_WIDTH, '') // 1) 폭 없는 글자 삭제
    .replace(TILDE, '') // 2) 물결표 삭제
    .replace(SPACES, ' ') // 3) 공백 여러 개(어떤 종류든) → 보통 공백 하나
    .replace(EDGE_SPACE, '') // 4) 앞뒤 공백 제거
    .toLowerCase() // 5) 대소문자 무시
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
  const a = answer.replace(ZERO_WIDTH, '') // 폭 없는 글자는 처음부터 없는 것으로
  a.split(',').forEach(add) // 1) 예전 규칙
  const cleaned = stripParentheses(a) // 2) 설명·표기 기호 걷어내기
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

/** 가타카나 → 히라가나 (읽기 채점용 — カンシャ로 써도 かんしゃ와 같게 본다) */
export function foldKana(s: string): string {
  return s.replace(/[\u30A1-\u30F6]/g, (ch) => String.fromCharCode(ch.charCodeAt(0) - 0x60))
}

/**
 * 읽기 채점 (온라인 시험지) — 뜻과 같은 후보 규칙에 가타카나·히라가나 구분 없이.
 * "(かいさいが)あやぶまれる" → "あやぶまれる"도 정답, "かど／つの" → 둘 중 하나.
 */
export function matchesReading(typed: string | null | undefined, reading: string | null | undefined): boolean {
  if (reading == null) return false
  return matchesAnswer(foldKana(typed ?? ''), foldKana(reading))
}
