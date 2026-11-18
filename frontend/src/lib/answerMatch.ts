// 직접 입력한 답의 채점 규칙 — 서버 com.vocamaster.common.AnswerMatcher 와 같은 규칙 (ADR-051).
// 두 구현은 src/test/resources/answer-match-cases.json 의 같은 사례로 검증한다
// (서버: AnswerMatcherTest, 프론트: npm test → scripts/test-answer-match.mjs).
//
// 정답 칸 하나에서 "이 중 하나만 맞히면 정답"인 후보를 뽑는다.
//  1) 예전 규칙 그대로 — 쉼표로 나눈 조각 (전에 정답이던 입력은 계속 정답)
//  2) 설명·표기 기호를 걷어낸 조각 — 괄호 설명 삭제, 영어 품사 표시(n. v. a. ad. …)와 ①②는 구분자,
//     , ， ; ； / ／ ・ 로 나눔
// 비교 전에: 폭 없는 글자 삭제 → 물결표(~ ～ 〜) 삭제 → 연속 공백 하나로 → 앞뒤 공백 제거 → 대소문자 무시.
//
// 2026-11-18 (ADR-062, 사용자 결정 — "띄어쓰기, 뜻을 앞뒤 섞어 적어도 맞게"):
//  · 한글·가나·한자가 든 낱개 뜻은 띄어쓰기를 빼고도 비교한다("피할수없는" = "피할 수 없는").
//    영어처럼 로마자뿐인 뜻은 띄어쓰기도 철자라 그대로 본다("app le" ≠ "apple") — 구분자로 묶인 답
//    "ice cream/아이스크림"에서도 영어 쪽은 그대로다("icecream/아이스크림"은 오답, Codex 8차 검산 P2).
//  · 입력에 뜻을 여러 개 적었으면(, ， ; ； / ／ ・ 로 나눔) 조각이 전부 후보일 때 정답 — 순서는 상관없다
//    ("끈기,고집" ← "n. 고집, 끈기"). 후보에 없는 뜻이 하나라도 섞이면 오답("사과, 포도" ← "사과, 능금").
// 정규식에 뒤보기(lookbehind)를 쓰지 않는다 — 오래된 iOS Safari에서 파일 전체가 깨진다.
//
// 공백은 SPACE 한 곳에 적은 글자만이다 — trim()·\s를 쓰지 않는다. Java의 trim()은 U+0020 이하만,
// JS의 trim()은 유니코드 공백까지 지워서, 앞뒤에 얇은 공백(U+2009) 같은 게 붙으면 서버(타이핑)와
// 화면(듣기·온라인 시험)의 채점이 갈렸다 (11/14 검토).

// 공백으로 보는 글자 (서버 AnswerMatcher.SPACE와 똑같이 유지) — 탭·줄바꿈 계열, 보통 공백, NEL(U+0085),
// NBSP(U+00A0), 오검 공백(U+1680), 여러 폭의 공백(U+2000~U+200A), 줄·문단 구분자(U+2028·2029),
// 좁은 NBSP(U+202F), 수학 공백(U+205F), 전각 공백(U+3000)
// 글자 그대로 서버와 같게 적는다 — 특히 VT는 \\v가 아니라 \\u000B: Java 정규식의 \v는 '세로 공백 묶음 전체'라 옮겨 적으면 함정
const SPACE = '[\\t\\n\\u000B\\f\\r \\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]'
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
// (JS의 $는 m 플래그가 없으면 문자열 맨 끝에서만 맞는다 — Java는 같은 뜻으로 \z를 쓴다)
const EDGE_SPACE = /^ | $/g
// 한글(자모·호환 자모·음절)·가나(히라가나·가타카나·반각 가타카나)·한자 — 이 글자가 든 낱개 뜻만 띄어쓰기를 빼고 비교한다 (ADR-062).
// 한자 범위는 testSheet.ts의 KANJI·detectLang.ts와 같다: 々〆 3005–3006, 확장 A 3400–4DBF, 통합 4E00–9FFF, 호환 F900–FAFF,
// BMP 밖 20000–3FFFF(u 플래그 필요) — 호환 한자·확장 한자만으로 된 답도 띄어쓰기를 무시한다 (Codex 8차 검산 P3, 11/18).
// 글자를 그대로 적지 않고 \u 표기로 적는다(도구의 정규화가 호환 한자를 바꾼다 — testSheet.ts). 서버 AnswerMatcher.CJK와 똑같이 유지
const CJK = /[\u1100-\u11FF\u3005\u3006\u3040-\u30FF\u3130-\u318F\u3400-\u4DBF\u4E00-\u9FFF\uAC00-\uD7A3\uF900-\uFAFF\uFF66-\uFF9F\u{20000}-\u{3FFFF}]/u
// 정규화한 뒤에는 보통 공백(U+0020)만 남는다
const PLAIN_SPACES = / /g

export function normalizeAnswer(s: string): string {
  return s
    .replace(ZERO_WIDTH, '') // 1) 폭 없는 글자 삭제
    .replace(TILDE, '') // 2) 물결표 삭제
    .replace(SPACES, ' ') // 3) 공백 여러 개(어떤 종류든) → 보통 공백 하나
    .replace(EDGE_SPACE, '') // 4) 앞뒤 공백 제거
    .toLowerCase() // 5) 대소문자 무시 — 대소문자 표는 실행 환경(브라우저)의 유니코드 버전을 따른다 (ADR-051 한계)
    .replace(/ς/g, 'σ') // 6) 끝 시그마 ς → 보통 시그마 σ — Σ를 소문자로 바꿀 때 Java와 JS의 고르는 규칙이 달라서
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

/**
 * 정답 칸을 '낱개 뜻'으로 나눈 조각 (정규화된 값) — 괄호 설명·품사 표시·①②를 걷어내고 모든 구분자로 나눈다.
 * 폭 없는 글자를 먼저 지운 문자열을 받는다.
 */
function cleanPieces(a: string): string[] {
  return stripParentheses(a)
    .replace(PART_OF_SPEECH, '$1,')
    .replace(CIRCLED_NUMBER, ',')
    .split(SEPARATORS)
    .map(normalizeAnswer)
    .filter((p) => p !== '')
}

/** 정답 칸에서 뽑은 후보 (정규화된 값) */
export function answerCandidates(answer: string | null | undefined): Set<string> {
  const out = new Set<string>()
  if (answer == null) return out
  // 폭 없는 글자는 처음부터 없는 것으로 — normalizeAnswer도 지우지만 여기서 먼저 지워야 한다:
  // "n.(제로폭)사과"에서 n. 뒤가 공백인지 보는 품사 판단이 그 글자에 가로막히기 때문
  const a = answer.replace(ZERO_WIDTH, '')
  for (const piece of a.split(',')) {
    const n = normalizeAnswer(piece) // 1) 예전 규칙
    if (n) out.add(n)
  }
  for (const p of cleanPieces(a)) out.add(p) // 2) 설명·표기 기호 걷어내기
  return out
}

/** 조각 하나가 후보와 같은가 — 그대로, 또는 (한글·가나·한자가 든 낱개 뜻에 한해) 띄어쓰기를 뺀 모양으로 */
function hits(piece: string, candidates: Set<string>, spaceless: Set<string>): boolean {
  return candidates.has(piece) || spaceless.has(piece.replace(PLAIN_SPACES, ''))
}

/**
 * 입력이 정답 칸들의 후보와 맞으면 true. 빈 입력·공백만은 항상 오답.
 *  · 입력 전체가 후보 하나와 같거나
 *  · 입력을 구분자로 나눈 조각이 전부 후보일 때 (뜻을 여러 개, 순서를 바꿔 적은 답 — ADR-062)
 */
export function matchesAnswer(typed: string | null | undefined, ...answers: Array<string | null | undefined>): boolean {
  const raw = (typed ?? '').replace(ZERO_WIDTH, '')
  const t = normalizeAnswer(raw)
  if (!t) return false
  const candidates = new Set<string>()
  const spaceless = new Set<string>()
  for (const answer of answers) {
    if (answer == null) continue
    for (const c of answerCandidates(answer)) candidates.add(c)
    // 띄어쓰기를 빼고 비교하는 것은 낱개 뜻뿐이다. "ice cream/아이스크림"처럼 구분자로 묶인 예전 후보를 통째로 쓰면
    // 한글이 섞여 있다는 이유로 영어 쪽 띄어쓰기까지 무시돼, "icecream/아이스크림"이 정답이 됐다 (Codex 8차 검산 P2, 11/18)
    for (const c of cleanPieces(answer.replace(ZERO_WIDTH, ''))) if (CJK.test(c)) spaceless.add(c.replace(PLAIN_SPACES, ''))
  }
  if (hits(t, candidates, spaceless)) return true
  const pieces = raw.split(SEPARATORS).map(normalizeAnswer).filter((p) => p !== '')
  return pieces.length > 0 && pieces.every((p) => hits(p, candidates, spaceless))
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
