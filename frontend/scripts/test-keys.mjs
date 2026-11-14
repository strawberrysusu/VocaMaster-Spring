// Enter 판정 검사 — 입력기 변환 확정 Enter는 저장·다음 칸으로 치지 않는다 (lib/keys.ts)
//   npm test --prefix frontend
import { loadTs } from './load-ts.mjs'

const keys = await loadTs(new URL('../src/lib/keys.ts', import.meta.url))
const ev = (key, keyCode, isComposing) => ({ key, keyCode, nativeEvent: { isComposing } })
const cases = [
  ['그냥 Enter', ev('Enter', 13, false), true],
  ['Chrome: 변환 중 Enter (isComposing)', ev('Enter', 229, true), false],
  ['Safari: 변환 확정 Enter (compositionend 뒤 keyCode 229)', ev('Enter', 229, false), false],
  ['isComposing만 켜진 Enter', ev('Enter', 13, true), false],
  ['Enter가 아닌 키', ev('a', 65, false), false],
]
let failed = 0
for (const [label, e, want] of cases) {
  const got = keys.isPlainEnter(e)
  if (got !== want) {
    failed++
    console.error(`FAIL ${label}: ${got} (기대 ${want})`)
  }
}
if (failed) {
  console.error(`\n${failed}/${cases.length} 실패`)
  process.exit(1)
}
console.log(`keys: ${cases.length}개 검사 모두 통과`)
