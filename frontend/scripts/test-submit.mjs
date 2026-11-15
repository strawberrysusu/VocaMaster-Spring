// 일괄 제출 나누기 검사 (lib/submitChunks.ts, 11/15) — 답이 1000개를 넘으면 묶음으로 나눠 보낸다
//   npm test --prefix frontend
import { loadTs } from './load-ts.mjs'

const { splitSubmission, SUBMIT_BATCH } = await loadTs(new URL('../src/lib/submitChunks.ts', import.meta.url))

let failed = 0
let total = 0
const check = (label, cond, detail = '') => {
  total++
  if (!cond) {
    failed++
    console.error(`FAIL ${label}${detail ? ' — ' + detail : ''}`)
  }
}

const UUID = '3f2b8c1e-9a4d-4e7b-b6c2-0d5e8f1a2001'   // 일부러 '001'로 끝나는 ID — 묶음 번호와 헷갈리면 안 된다
const answers = (n) => Object.fromEntries(Array.from({ length: n }, (_, i) => [i + 1, i % 3 !== 0]))

check('서버 상한과 같은 크기', SUBMIT_BATCH === 1000)

const one = splitSubmission(UUID, answers(1000))
check('1000개 이하는 묶음 하나, ID 그대로', one.length === 1 && one[0].submissionId === UUID && one[0].answers.length === 1000)

const three = splitSubmission(UUID, answers(2345))
check('2345개 = 1000 + 1000 + 345', three.map((c) => c.answers.length).join(',') === '1000,1000,345')
check('첫 묶음은 원래 ID', three[0].submissionId === UUID)
check('둘째부터는 앞 32자 + ~ + 번호', three[1].submissionId === UUID.slice(0, 32) + '~001' && three[2].submissionId === UUID.slice(0, 32) + '~002')
check('ID는 서로 다르고 36자 이하', new Set(three.map((c) => c.submissionId)).size === 3 && three.every((c) => c.submissionId.length <= 36))
const flat = three.flatMap((c) => c.answers)
check('빠지거나 겹치는 카드 없음, 카드 번호 순', flat.length === 2345 && flat.every((a, i) => a.cardId === i + 1))
check('답 값 그대로', flat[0].correct === false && flat[1].correct === true)

// 같은 답안이면 넣은 순서가 달라도 같은 묶음 — 다시 보낼 때 서버가 '같은 제출'로 알아보는 조건
const shuffled = Object.fromEntries(Object.entries(answers(2345)).reverse())
check('순서가 달라도 같은 묶음', JSON.stringify(splitSubmission(UUID, shuffled)) === JSON.stringify(three))

const short = splitSubmission('s-1794659032954-abcdefgh', answers(1001))   // randomUUID가 없는 브라우저의 대체 ID
check('짧은 대체 ID도 된다', short.length === 2 && short[1].submissionId === 's-1794659032954-abcdefgh~001' && short[1].answers.length === 1)
check('빈 답안은 묶음 없음', splitSubmission(UUID, {}).length === 0)

if (failed) {
  console.error(`${failed}/${total} 실패`)
  process.exit(1)
}
console.log(`submit: ${total}개 검사 모두 통과`)
