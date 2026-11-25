// '비슷하게 쓴 답' 판별(src/lib/answerMatch.ts isNearMiss) 검사 — 온라인 시험 결과에서 틀린 답을
// '비슷하게 썼어요'(위)와 '틀렸어요'(아래)로 나누는 데만 쓴다. 채점은 바꾸지 않는다 (ADR-066).
//   npm test --prefix frontend
import { loadTs } from './load-ts.mjs'

const m = await loadTs(new URL('../src/lib/answerMatch.ts', import.meta.url))

const cases = [
  // 위로 모을 것 — 정답과 겹치는 데가 있다
  ['꼬리,첨부하다', 'n. 꼬리, 끝 v. 미행하다', true, '뜻 여러 개 중 일부만 맞음'],
  ['당기다', 'v. 잡아당기다, 끌다', true, '한쪽이 다른 쪽을 품음'],
  ['이빨', 'n. 이, 치아', true, '한 글자 뜻(이)을 품은 답'],
  ['사진가', 'n. 사진사', true, '앞 두 글자가 같음'],
  ['엳듣다', 'v. 우연히 듣다, 엿듣다', true, '한 글자만 다름(오타)'],
  ['놀래키다', 'v. 놀라게 하다', true, '자모 넷이 앞에서 같음 (놀래/놀라)'],
  ['기쁜', 'n. 기쁨 v. 기쁘게 하다', true, '자모 넷이 앞에서 같음 (기쁜/기쁨)'],
  ['~받을만한', 'v. ~을 받을 만하다', true, '가운데 세 글자 이상 겹침'],
  ['아무것도', 'pron. 아무도[아무것도] ~않다', true, '대괄호를 푼 뜻과 앞부분이 같음 (ADR-066)'],
  ['banan', 'banana', true, '영어도 네 글자 이상 품으면'],
  // 아래로 보낼 것 — 겹치는 데가 없다
  ['건강한', 'n. 부, 재산', false, '비슷하게 생긴 다른 단어(health)의 뜻'],
  ['결혼하다', 'a. 명랑한, 즐거운', false, '비슷하게 생긴 다른 단어(marry)의 뜻'],
  ['걱정하다', 'n. 공포 v. 두려워하게 하다, 놀라게 하다', false, "끝의 '하다'만 같은 것은 치지 않음"],
  ['실망시키다', 'v. 익사시키다', false, "둘 다의 끝 '시키다'만 같은 것은 치지 않음"],
  ['하지만', 'conj. 그러나', false, '뜻은 같아도 겹치는 글자가 없으면 못 잡는다 (사람이 본다)'],
  ['app', 'apple', false, '영어는 세 글자 겹침으로는 부족'],
  ['', 'n. 사과', false, '빈 답'],
  ['   ', 'n. 사과', false, '공백만'],
]

let failed = 0
for (const [typed, answer, expected, why] of cases) {
  const actual = m.isNearMiss(typed, answer)
  if (actual !== expected) {
    failed++
    console.error(`FAIL [${typed}] ← ${answer} — 기대 ${expected}, 실제 ${actual} (${why})`)
  }
}

if (failed) {
  console.error(`\n${failed}/${cases.length} 실패`)
  process.exit(1)
}
console.log(`nearMiss: ${cases.length}개 검사 모두 통과`)
