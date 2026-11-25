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
//  · 입력에 적은 괄호 설명·품사 표시·①②도 정답 칸처럼 걷어내고 본다 (같은 날 보완 2, Codex 9차 검산) —
//    "피할수없는 (불가피한)" ← "피할 수 없는 (불가피한)", "a. 피할수없는" ← "a. 피할 수 없는".
//    위 두 규칙으로 안 맞을 때만 쓰는 마지막 단계라, 전에 정답이던 입력이 오답으로 바뀌는 일은 없다.
// 정규식에 뒤보기(lookbehind)를 쓰지 않는다 — 오래된 iOS Safari에서 파일 전체가 깨진다.
//
// 공백은 SPACE 한 곳에 적은 글자만이다 — trim()·\s를 쓰지 않는다. Java의 trim()은 U+0020 이하만,
// JS의 trim()은 유니코드 공백까지 지워서, 앞뒤에 얇은 공백(U+2009) 같은 게 붙으면 서버(타이핑)와
// 화면(듣기·온라인 시험)의 채점이 갈렸다 (11/14 검토).

// 공백으로 보는 글자 (서버 AnswerMatcher.SPACE와 똑같이 유지) — 탭·줄바꿈 계열, 보통 공백, NEL(U+0085),
// NBSP(U+00A0), 오검 공백(U+1680), 여러 폭의 공백(U+2000~U+200A), 줄·문단 구분자(U+2028·2029),
// 좁은 NBSP(U+202F), 수학 공백(U+205F), 전각 공백(U+3000)
// 글자 그대로 서버와 같게 적는다 — 특히 VT는 \\v가 아니라 \\u000B: Java 정규식의 \v는 '세로 공백 묶음 전체'라 옮겨 적으면 함정
const SPACE_CHARS = '\\t\\n\\u000B\\f\\r \\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000'
const SPACE = '[' + SPACE_CHARS + ']'
// 대괄호 [ ] (ADR-066, 11/25) — 카드가 쓰는 두 모양 (19,156장 중 3장):
//  · 낱말에 바로 붙은 [ ] = 그 낱말 대신 쓸 수 있는 말: "아무도[아무것도] ~않다" → "아무도 ~않다" 또는 "아무것도 ~않다"
//  · 띄어 쓴 [ ] = 설명(발음 기호 등) → 지운다: "n. [ˈɪnsens] 향" → "n. 향"
// 서버 AnswerMatcher.ALT_BRACKET·NOTE_BRACKET과 똑같이 유지
const ALT_BRACKET = new RegExp('([^' + SPACE_CHARS + '\\[\\]［］()（）,，;；/／・]+)[\\[［]([^\\[\\]［］]+)[\\]］]')
const NOTE_BRACKET = /[[［][^[\]［］]*[\]］]/g
// 한 칸에 '대신 쓸 수 있는 말'이 이보다 많으면 나머지 [ ]는 설명으로 보고 지운다 (경우의 수가 2^n으로 늘지 않게)
const MAX_BRACKET_ALTS = 4
// 짧은 괄호 (ADR-066 보완, 11/25) — 안에 구분자·쌍점·마침표·등호가 없는 괄호. 정답 칸에서는 이 괄호를 '벗긴 모양'도
// 후보로 본다: "(돈을) 걸다" → "돈을 걸다". 구분자가 든 설명 괄호("(fill out/in)")·품사 표시가 든 괄호("(a. tenacious)")는
// 벗기지 않는다 — 괄호 속 낱말 하나가 정답이 되지 않게. 서버 AnswerMatcher.SIMPLE_PAREN과 똑같이 유지
const SIMPLE_PAREN = /[(（]([^()（）,，;；/／・:：.=]*)[)）]/g
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
 * 대괄호를 푼 모양들 (ADR-066) — 낱말에 붙은 [ ]마다 '앞 낱말 그대로'와 '[ ] 안의 말로 바꾼 것' 두 갈래,
 * 남은 [ ](띄어 쓴 것·너무 많은 것)는 지운다. [ ]가 없으면 받은 문자열 하나.
 */
function bracketVariants(s: string, depth = 0): string[] {
  const m = depth < MAX_BRACKET_ALTS ? ALT_BRACKET.exec(s) : null
  if (!m) return [s.replace(NOTE_BRACKET, '')]
  const head = s.slice(0, m.index)
  const tail = s.slice(m.index + m[0].length)
  return [...bracketVariants(head + m[1] + tail, depth + 1), ...bracketVariants(head + m[2] + tail, depth + 1)]
}

/**
 * 정답 칸을 '낱개 뜻'으로 나눈 조각 (정규화된 값) — 대괄호를 풀고(ADR-066), 괄호 설명·품사 표시·①②를 걷어내고
 * 모든 구분자로 나눈다. 폭 없는 글자를 먼저 지운 문자열을 받는다.
 */
function cleanPieces(a: string): string[] {
  return bracketVariants(a).flatMap((v) => splitPieces(stripParentheses(v)))
}

/** 괄호를 걷어낸 문자열 → 품사 표시·①②·구분자로 나눈 조각 (정규화된 값) */
function splitPieces(cleaned: string): string[] {
  return cleaned
    .replace(PART_OF_SPEECH, '$1,')
    .replace(CIRCLED_NUMBER, ',')
    .split(SEPARATORS)
    .map(normalizeAnswer)
    .filter((p) => p !== '')
}

/**
 * 정답 칸에만 쓰는 조각 (ADR-066 보완) — 짧은 괄호는 벗기고 나머지 괄호는 지운 모양.
 * 짧은 괄호가 없으면 빈 배열 (cleanPieces와 같아지므로). 입력 쪽에는 쓰지 않는다 — 입력의 괄호 안은 전처럼 채점하지 않는다
 */
function unwrappedPieces(a: string): string[] {
  return bracketVariants(a).flatMap((v) => {
    const u = v.replace(SIMPLE_PAREN, ' $1 ')
    return u === v ? [] : splitPieces(stripParentheses(u))
  })
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
  for (const p of unwrappedPieces(a)) out.add(p) // 3) 짧은 괄호는 벗긴 모양도 (ADR-066 보완)
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
 *  · 입력에서 괄호 설명·품사 표시·①②를 걷어낸 조각이 전부 후보일 때 (ADR-062 보완 2)
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
    const a = answer.replace(ZERO_WIDTH, '')
    for (const c of [...cleanPieces(a), ...unwrappedPieces(a)]) if (CJK.test(c)) spaceless.add(c.replace(PLAIN_SPACES, ''))
  }
  if (hits(t, candidates, spaceless)) return true
  const allHit = (pieces: string[]) => pieces.length > 0 && pieces.every((p) => hits(p, candidates, spaceless))
  if (allHit(raw.split(SEPARATORS).map(normalizeAnswer).filter((p) => p !== ''))) return true
  // 입력도 정답 칸과 같은 방법으로 걷어내고 본다 — 카드에 적힌 대로 괄호 설명·품사 표시까지 따라 적은 답
  // ("피할수없는 (불가피한)", "a. 피할수없는"). 괄호 안에만 적은 입력은 조각이 하나도 안 남아 오답이다
  return allHit(cleanPieces(raw))
}

// ── 비슷하게 쓴 답 (ADR-066, 11/25) — 화면에만 있다(서버에는 없다) ──
// 채점은 바꾸지 않는다. 온라인 시험 결과에서 틀린 답 가운데 정답과 겹치는 데가 있는 답을 위로 모아,
// 사람이 '맞음으로'를 고르기 쉽게 하는 데만 쓴다 (11/25 시험 75문항으로 맞춰 본 규칙 — ADR-066).
const LATIN_ONLY = /^[a-z0-9 '.-]+$/

/** 한 글자만 다른가 (바꾸기·넣기·빼기 하나) */
function oneEditApart(a: string, b: string): boolean {
  if (a === b || Math.abs(a.length - b.length) > 1) return false
  let i = 0
  while (i < a.length && i < b.length && a[i] === b[i]) i++
  let j = 0
  while (j < a.length - i && j < b.length - i && a[a.length - 1 - j] === b[b.length - 1 - j]) j++
  return Math.max(a.length, b.length) - i - j === 1
}

/** 한글 음절을 자모(초성·중성·종성) 번호로 — 앞부분이 같은지 글자보다 잘게 본다 (놀래키다/놀라게 하다, 기쁜/기쁨) */
function jamo(s: string): number[] {
  const out: number[] = []
  for (const ch of s) {
    const code = ch.codePointAt(0)!
    if (code >= 0xac00 && code <= 0xd7a3) {
      const n = code - 0xac00
      out.push(0x1100 + Math.floor(n / 588), 0x1161 + Math.floor((n % 588) / 28))
      if (n % 28) out.push(0x11a7 + (n % 28))
    } else out.push(code)
  }
  return out
}

function commonPrefix(a: ArrayLike<unknown>, b: ArrayLike<unknown>): number {
  let k = 0
  while (k < a.length && k < b.length && a[k] === b[k]) k++
  return k
}

/** 가장 길게 겹치는 부분의 길이 — 둘 다의 끝(어미 '시키다' 같은)에서만 겹치는 것은 세지 않는다 */
function longestSharedMiddle(a: string, b: string): number {
  let best = 0
  const prev = new Array<number>(b.length + 1).fill(0)
  for (let i = 1; i <= a.length; i++) {
    let diag = 0
    for (let j = 1; j <= b.length; j++) {
      const up = prev[j]
      prev[j] = a[i - 1] === b[j - 1] ? diag + 1 : 0
      diag = up
      if (prev[j] > best && !(i === a.length && j === b.length)) best = prev[j]
    }
  }
  return best
}

/** 낱개 뜻 둘(띄어쓰기를 뺀 모양)이 가까운가 */
function closePiece(p: string, c: string): boolean {
  if (p === c) return true // 뜻 여러 개 중 일부만 맞음
  const latin = LATIN_ONLY.test(p) && LATIN_ONLY.test(c)
  const min = latin ? 4 : 2 // 영어는 짧은 겹침이 흔해서 더 길게 본다
  const [short, long] = p.length <= c.length ? [p, c] : [c, p]
  if (short.length >= min && long.includes(short)) return true // 당기다 ⊂ 잡아당기다
  if (!latin && c.length === 1 && p.includes(c)) return true // 한 글자 뜻을 품은 답: 이 ⊂ 이빨, 쥐 ⊂ 들쥐
  if (commonPrefix(p, c) >= min) return true // 앞부분이 같음: 사진가/사진사, 두려워하다/두려워하는
  if (Math.min(p.length, c.length) >= min + 1 && oneEditApart(p, c)) return true // 한 글자만 다름: 엳듣다/엿듣다
  if (latin) return false
  // 한글은 더 잘게: 자모 넷 이상이 앞에서 같거나(놀래키다/놀라게 하다, 기쁜/기쁨),
  // 가운데가 세 글자 이상 겹친다(받을만한/~을 받을 만하다)
  return commonPrefix(jamo(p), jamo(c)) >= 4 || longestSharedMiddle(p, c) >= 3
}

/**
 * 틀린 답이 정답과 가까운가 — 입력의 낱개 뜻 하나라도 정답의 낱개 뜻과 같거나, 한쪽이 다른 쪽을 품거나,
 * 앞 두 글자(영어는 네 글자) 이상이 같거나, 한 글자만 다르면 true. 빈 입력은 false.
 * 뜻은 같은데 겹치는 글자가 없는 답(하지만/그러나)은 못 잡는다 — 그건 사람이 본다.
 */
export function isNearMiss(typed: string | null | undefined, ...answers: Array<string | null | undefined>): boolean {
  const raw = (typed ?? '').replace(ZERO_WIDTH, '')
  if (!normalizeAnswer(raw)) return false
  const squash = (s: string) => s.replace(PLAIN_SPACES, '')
  const typedPieces = new Set([...raw.split(SEPARATORS).map(normalizeAnswer), ...cleanPieces(raw)].filter((p) => p !== '').map(squash))
  const candidates = new Set<string>()
  for (const a of answers) if (a != null) for (const c of cleanPieces(a.replace(ZERO_WIDTH, ''))) candidates.add(squash(c))
  for (const p of typedPieces) for (const c of candidates) if (closePiece(p, c)) return true
  return false
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
