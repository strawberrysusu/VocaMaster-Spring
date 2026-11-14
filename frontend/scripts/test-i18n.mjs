// 번역 사전 검사 (ADR-056) — npm test --prefix frontend
//  · 일본어·영어·중국어 사전의 열쇠가 똑같은지 (tsc도 잡지만 여기서 한 번 더, 숫자로 보이게)
//  · 값이 비지 않았는지, {n} 같은 자리가 열쇠와 똑같은지, 번역에 한글이 남지 않았는지
//  · 코드의 t('…') 열쇠가 모두 사전에 있는지 (src 전체를 훑는다)
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { loadTs } from './load-ts.mjs'

const here = (p) => new URL(p, import.meta.url)
const { ja } = await loadTs(here('../src/i18n/ja.ts'))
const { en } = await loadTs(here('../src/i18n/en.ts'))
const { zh } = await loadTs(here('../src/i18n/zh.ts'))

let failed = 0
const fail = (msg) => {
  failed++
  console.error('FAIL ' + msg)
}
const HANGUL = /[\uAC00-\uD7A3]/
const holes = (s) => (s.match(/\{\w+\}/g) ?? []).sort().join(',')

const jaKeys = Object.keys(ja)
const enKeys = Object.keys(en)
for (const k of jaKeys) if (!(k in en)) fail(`영어 사전에 없음: ${k}`)
for (const k of enKeys) if (!(k in ja)) fail(`일본어 사전에 없음: ${k}`)
for (const k of jaKeys) if (!(k in zh)) fail(`중국어 사전에 없음: ${k}`)
for (const k of Object.keys(zh)) if (!(k in ja)) fail(`일본어 사전에 없음(중국어에만): ${k}`)

for (const [name, dict] of [['ja', ja], ['en', en], ['zh', zh]]) {
  for (const [k, v] of Object.entries(dict)) {
    if (typeof v !== 'string' || v.trim() === '') fail(`${name} 빈 값: ${k}`)
    else {
      if (holes(v) !== holes(k)) fail(`${name} 자리 다름: ${k} → ${v}`)
      if (HANGUL.test(v) && k !== '한국어') fail(`${name} 번역에 한글: ${k} → ${v}`)
    }
  }
}

// 코드의 t('…') / t("…") 열쇠 — 문자열 하나로 된 것만 (변수로 만든 열쇠는 tsc가 Key 타입으로 막는다)
const srcDir = new URL('../src/', import.meta.url)
const i18nDir = new URL('i18n/', srcDir) // 사전 폴더는 빼고 (경로 비교 — 상위 폴더 이름에 i18n이 있어도 안전)
const files = []
const walk = (dir) => {
  for (const name of readdirSync(dir)) {
    const p = new URL(name, dir)
    if (statSync(p).isDirectory()) walk(new URL(name + '/', dir))
    else if (/\.(ts|tsx)$/.test(name) && !p.href.startsWith(i18nDir.href)) files.push(p)
  }
}
walk(srcDir)
// 소스의 문자열 이스케이프를 실제 글자로 (\n \t \' \" \\)
const unescape = (raw) => raw.replace(/\\([ntr'"\\])/g, (_, c) => ({ n: '\n', t: '\t', r: '\r' })[c] ?? c)
const used = new Set()
const CALL = /\bt\(\s*(['"])((?:\\.|(?!\1).)*)\1/g
for (const f of files) {
  const text = readFileSync(f, 'utf8')
  for (const m of text.matchAll(CALL)) used.add(unescape(m[2]))
}
for (const k of used) if (!(k in ja)) fail(`코드에서 쓰는데 사전에 없음: ${k}`)

if (failed) {
  console.error(`\n${failed}개 실패`)
  process.exit(1)
}
console.log(`i18n: 열쇠 ${jaKeys.length}개 (코드에서 쓰는 것 ${used.size}개) — 일본어·영어·중국어 모두 통과`)
