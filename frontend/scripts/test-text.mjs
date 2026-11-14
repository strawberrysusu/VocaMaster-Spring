// 글자 처리 검사 — JWT 이메일을 UTF-8로 읽기(jwt.ts), 발음 언어 판별(detectLang.ts) (11/14 검토)
//   npm test --prefix frontend
import { loadTs } from './load-ts.mjs'

const here = (p) => new URL(p, import.meta.url)
const { jwtEmail } = await loadTs(here('../src/lib/jwt.ts'))
const { detectLang } = await loadTs(here('../src/lib/detectLang.ts'))

let failed = 0
let total = 0
const check = (label, got, want) => {
  total++
  if (got !== want) {
    failed++
    console.error(`FAIL ${label} — ${JSON.stringify(got)} (기대 ${JSON.stringify(want)})`)
  }
}

// 서버가 만드는 것과 같은 꼴의 토큰: base64url(헤더).base64url(본문).서명
const b64url = (obj) => Buffer.from(JSON.stringify(obj), 'utf8').toString('base64url')
const token = (payload) => `${b64url({ alg: 'HS256' })}.${b64url(payload)}.sig`

check('ASCII 이메일', jwtEmail(token({ email: 'user@example.com' })), 'user@example.com')
check('한글 이메일 (atob만 쓰면 깨짐)', jwtEmail(token({ email: '홍길동@example.com' })), '홍길동@example.com')
check('일본어 이메일', jwtEmail(token({ email: 'たろう@example.jp' })), 'たろう@example.jp')
check('토큰 없음', jwtEmail(null), '')
check('깨진 토큰', jwtEmail('not-a-token'), '')
check('이메일 칸 없음', jwtEmail(token({ sub: '2' })), '')

check('가나', detectLang('かいぎ'), 'ja-JP')
check('BMP 한자', detectLang('会議'), 'ja-JP')
check('BMP 밖 한자만 (𩸽 U+29E3D)', detectLang('𩸽'), 'ja-JP')
check('한글', detectLang('사과'), 'ko-KR')
check('영어', detectLang('meticulous'), 'en-US')

if (failed) {
  console.error(`${failed}/${total} 실패`)
  process.exit(1)
}
console.log(`text: ${total}개 검사 모두 통과`)
