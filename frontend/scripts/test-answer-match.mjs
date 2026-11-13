// 프론트 채점 규칙(src/lib/answerMatch.ts) 검사 — 서버 AnswerMatcherTest 와 같은 사례표를 쓴다 (ADR-051).
// 테스트 러너를 새로 들이지 않고, 이미 있는 typescript 로 .ts 를 JS로 바꿔 불러온다 (CI Node 20에서도 동작).
//   npm test --prefix frontend
import { readFileSync } from 'node:fs'
import { loadTs } from './load-ts.mjs'

const here = (p) => new URL(p, import.meta.url)

const mod = await loadTs(here('../src/lib/answerMatch.ts'))

const cases = JSON.parse(readFileSync(here('../../src/test/resources/answer-match-cases.json'), 'utf8'))

let failed = 0
const check = (label, actual, expected, why) => {
  if (actual !== expected) {
    failed++
    console.error(`FAIL ${label} — 기대 ${expected}, 실제 ${actual} (${why})`)
  }
}

if (cases.length < 40) {
  failed++
  console.error(`FAIL 사례표가 너무 적다: ${cases.length}개`)
}
for (const c of cases) {
  check(`[${c.typed}] ← ${c.answer}`, mod.matchesAnswer(c.typed, c.answer), c.expected, c.why)
}

// 프론트에만 있는 쓰임: 정답 칸 여러 개(듣기 모드의 단어 + 읽기)
check('읽기 칸으로 정답', mod.matchesAnswer('かんしゃ', '感謝', 'かんしゃ'), true, '단어 또는 읽기 어느 쪽이든')
check('읽기 두 가지', mod.matchesAnswer('つの', '角', 'かど／つの'), true, '읽기의 전각 빗금')
check('예문 괄호 읽기', mod.matchesAnswer('あやぶまれる', '(開催が)危ぶまれる', '(かいさいが)あやぶまれる'), true, '읽기의 괄호 문맥')
check('읽기 없는 카드', mod.matchesAnswer('apple', 'apple', null), true, 'null 칸은 건너뜀')
check('빈 입력', mod.matchesAnswer('', '感謝', 'かんしゃ'), false, '빈 입력은 오답')
check('null 입력', mod.matchesAnswer(null, '感謝'), false, 'null 입력은 오답')

const total = cases.length + 6
if (failed) {
  console.error(`\n${failed}/${total} 실패`)
  process.exit(1)
}
console.log(`answerMatch: ${total}개 사례 모두 통과`)
