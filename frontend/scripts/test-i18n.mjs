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

// 서버 오류 문구 번역 (serverText.ts) — 통째·{n} 틀·입력 검사 조각·모르는 문구
const { translateServerMessage } = await loadTs(here('../src/i18n/serverText.ts'))
const expectServer = (label, got, want) => {
  if (got !== want) fail(`서버 문구 ${label}: ${got} (기대 ${want})`)
}
const LOGIN = '이메일 또는 비밀번호가 올바르지 않습니다'
const QUIZ = '퀴즈에는 덱에 카드가 최소 2개 필요합니다. 현재 {n}개'
const LINES = '한 번에 최대  {max}줄까지 등록할 수 있습니다. 현재 {n}줄입니다.'
for (const [name, dict] of [['ja', ja], ['en', en], ['zh', zh]]) {
  const S = (msg) => translateServerMessage(msg, dict)
  expectServer(`${name} 통째`, S(LOGIN), dict[LOGIN])
  expectServer(`${name} {n} 틀`, S('퀴즈에는 덱에 카드가 최소 2개 필요합니다. 현재 1개'), dict[QUIZ].replace('{n}', '1'))
  expectServer(`${name} 두 칸 공백 틀`, S('한 번에 최대  1000줄까지 등록할 수 있습니다. 현재 1203줄입니다.'), dict[LINES].replace('{max}', '1000').replace('{n}', '1203'))
  expectServer(`${name} 입력 검사 조각`, S('nickname: 닉네임은 1~30자, password: 비밀번호는 8자 이상'), `nickname: ${dict['닉네임은 1~30자']}, password: ${dict['비밀번호는 8자 이상']}`)
  expectServer(`${name} 모르는 문구는 그대로`, S('전혀 모르는 문구'), '전혀 모르는 문구')
}

// 가져오기 실패 줄의 '너무 김' 꼬리 (serverText.ts) — 서버 코드(ImportService)가 붙이는 꼬리와 글자까지 같아야 번역된다
const { translateImportLine, TOO_LONG_SUFFIX } = await loadTs(here('../src/i18n/serverText.ts'))
const importJava = readFileSync(new URL('../../src/main/java/com/vocamaster/cardimport/ImportService.java', import.meta.url), 'utf8')
const javaInt = (name) => Number(new RegExp(`${name} = (\\d+);`).exec(importJava)?.[1])
const javaSuffix = `  (너무 김: 단어·뜻 ${javaInt('MAX_TEXT')}자, 읽기 ${javaInt('MAX_READING')}자 이내)`
if (!importJava.includes('"  (너무 김: 단어·뜻 " + MAX_TEXT + "자, 읽기 " + MAX_READING + "자 이내)"') || javaSuffix !== TOO_LONG_SUFFIX)
  fail(`가져오기 '너무 김' 꼬리가 서버 코드와 다름: ${javaSuffix}`)
for (const [name, dict] of [['ja', ja], ['en', en], ['zh', zh]]) {
  const line = '会議 | かいぎ | ' + 'x'.repeat(300)
  expectServer(`${name} 가져오기 너무 김`, translateImportLine(line + TOO_LONG_SUFFIX, dict), line + dict[TOO_LONG_SUFFIX])
  expectServer(`${name} 가져오기 줄 내용은 그대로`, translateImportLine('a | b | c | d', dict), 'a | b | c | d')
}

// 영어 단수·복수 (english.ts) — 수가 1이면 단수, 채운 값(사용자 글)은 그대로
const { englishOne } = await loadTs(here('../src/i18n/english.ts'))
const fillEn = (text, vars) => englishOne(text, vars).replace(/\{(\w+)\}/g, (whole, n) => (n in vars ? String(vars[n]) : whole))
const expectEn = (label, got, want) => {
  if (got !== want) fail(`영어 단수 ${label}: ${got} (기대 ${want})`)
}
expectEn('1', fillEn('{n} cards', { n: 1 }), '1 card')
expectEn('2', fillEn('{n} cards', { n: 2 }), '2 cards')
expectEn('11', fillEn('{n} cards', { n: 11 }), '11 cards')
expectEn('자리 둘', fillEn('{decks} decks · {cards} cards', { decks: 1, cards: 3 }), '1 deck · 3 cards')
expectEn('채운 값은 그대로', fillEn('"{answer}"', { answer: '1 days' }), '"1 days"')
// 영어 사전 전체: 수가 들어가는 자리를 모두 1로 채워도 '1 …s'(복수)가 남지 않는다
const TEXT_VARS = new Set(['name', 'word', 'title', 'answer', 'status', 'sheet', 'msg', 'message', 'date', 'content', 'preview', 'prefix', 'names', 'error', 'email', 'ago', 'id'])
for (const [k, v] of Object.entries(en)) {
  const vars = {}
  for (const [, n] of v.matchAll(/\{(\w+)\}/g)) vars[n] = TEXT_VARS.has(n) ? 'X' : 1
  const out = fillEn(v, vars)
  if (/(^|[^\d.,/])1 ([a-z]+ )?[a-z]+s\b/.test(out)) fail(`영어 1일 때 복수: ${k} → ${out}`)
}

if (failed) {
  console.error(`\n${failed}개 실패`)
  process.exit(1)
}
console.log(`i18n: 열쇠 ${jaKeys.length}개 (코드에서 쓰는 것 ${used.size}개) — 일본어·영어·중국어 모두 통과`)
