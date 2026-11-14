// 화면 문구 번역 — 다국어 (11/14, ADR-056)
//
// · 한국어 문구 자체가 열쇠다: t('채점하기') → 고른 언어의 문구. 한국어 화면은 열쇠를 그대로 보여 주니 예전과 똑같다.
// · 일본어·영어·중국어는 사전(ja.ts·en.ts·zh.ts)에서 찾는다. 세 사전은 열쇠가 똑같아야 한다 —
//   en·zh의 타입이 Messages(= ja의 열쇠 목록)라서 빠지거나 남는 열쇠는 tsc가 잡는다. t()에 사전에 없는 열쇠를 넣어도 tsc가 잡는다.
// · {n} 같은 자리는 vars로 채운다: t('틀린 {n}개만 다시 풀기', { n: 3 })
// · 서버가 보내는 오류 문구(한국어)는 tServer()로 — 사전에서 찾아 번역한다(아래)
// · 언어는 설정(브라우저 저장 vm.settings.lang)에서 고르고, 없으면 브라우저 언어를 따른다(일·영·중이 아니면 한국어).
//   바꾸면 새로고침한다 — 파일이 처음 불릴 때 정해진 문구까지 새 언어로 바뀌게.
import { ja, type Messages } from './ja'
import { en } from './en'
import { zh } from './zh'

export type Lang = 'ko' | 'ja' | 'en' | 'zh'
export type Key = keyof Messages

/** 언어 고르기 목록 — 이름은 그 언어로 적는다(자기 언어를 찾기 쉽게) */
export const LANGS: { code: Lang; label: string }[] = [
  { code: 'ko', label: '한국어' },
  { code: 'ja', label: '日本語' },
  { code: 'en', label: 'English' },
  { code: 'zh', label: '中文' },
]

const DICTS: Record<Exclude<Lang, 'ko'>, Messages> = { ja, en, zh }
const SETTINGS_KEY = 'vm.settings' // lib/settings.ts와 같은 저장 칸

function isLang(v: unknown): v is Lang {
  return v === 'ko' || v === 'ja' || v === 'en' || v === 'zh'
}

function detectLang(): Lang {
  try {
    const saved = JSON.parse(localStorage.getItem(SETTINGS_KEY) ?? '{}').lang
    if (isLang(saved)) return saved
  } catch {
    /* 저장소가 막혀도 브라우저 언어로 */
  }
  const nav = (typeof navigator !== 'undefined' ? navigator.language : '').toLowerCase()
  if (nav.startsWith('ja')) return 'ja'
  if (nav.startsWith('en')) return 'en'
  if (nav.startsWith('zh')) return 'zh'
  return 'ko'
}

let current: Lang = detectLang()
// html lang — 글꼴이 그 언어 모양(한자 자형 등)으로 고르게. 중국어는 간체(zh-CN)
if (typeof document !== 'undefined') document.documentElement.lang = current === 'zh' ? 'zh-CN' : current

export function getLang(): Lang {
  return current
}

/** 고른 언어의 문구. 사전에 없으면(있을 수 없지만) 한국어 그대로 */
export function t(key: Key, vars?: Record<string, string | number>): string {
  const text: string = current === 'ko' ? key : (DICTS[current][key] ?? key)
  if (!vars) return text
  return text.replace(/\{(\w+)\}/g, (whole, name: string) => (name in vars ? String(vars[name]) : whole))
}

// ── 서버 오류 문구 (서버는 한국어로 보낸다)
// 숫자 같은 값이 끼는 문구('현재 1개')는 사전의 {n} 틀('현재 {n}개')과 모양을 맞춰 본다. 틀은 처음 쓸 때 한 번 만든다
let patterns: { re: RegExp; key: Key; names: string[] }[] | null = null
function templatePatterns() {
  if (!patterns) {
    patterns = (Object.keys(ja) as Key[])
      .filter((key) => key.includes('{'))
      .map((key) => {
        const names: string[] = []
        const source = key
          .split(/(\{\w+\})/)
          .map((part) => {
            const hole = /^\{(\w+)\}$/.exec(part)
            if (hole) {
              names.push(hole[1])
              return '(.+?)'
            }
            return part.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
          })
          .join('')
        return { re: new RegExp('^' + source + '$'), key, names }
      })
  }
  return patterns
}

function lookupServerText(text: string): string | null {
  if (text in ja) return t(text as Key)
  for (const { re, key, names } of templatePatterns()) {
    const m = re.exec(text)
    if (m) return t(key, Object.fromEntries(names.map((name, i) => [name, m[i + 1]])))
  }
  return null
}

/**
 * 서버가 보낸 오류 문구를 고른 언어로.
 * · 통째로 사전에 있거나 {n} 틀과 맞으면 번역
 * · 입력 검사 오류('nickname: 닉네임은 1~30자, password: …')는 조각마다 — 필드 이름은 그대로
 * · 모르는 문구는 그대로 둔다 — 뜻이 사라지는 것보다 한국어라도 보이는 게 낫다
 */
export function tServer(message: string): string {
  if (current === 'ko' || !message) return message
  const whole = lookupServerText(message)
  if (whole !== null) return whole
  return message
    .split(', ')
    .map((part) => {
      const field = /^([A-Za-z_][\w.[\]]*): (.+)$/.exec(part)
      if (field) return `${field[1]}: ${lookupServerText(field[2]) ?? field[2]}`
      return lookupServerText(part) ?? part
    })
    .join(', ')
}

/** 언어 바꾸기 — 설정에 저장하고 새로고침 */
export function changeLang(lang: Lang): void {
  try {
    const saved = JSON.parse(localStorage.getItem(SETTINGS_KEY) ?? '{}')
    localStorage.setItem(SETTINGS_KEY, JSON.stringify({ ...saved, lang }))
  } catch {
    /* 저장이 막히면 이번 화면만 */
  }
  current = lang
  window.location.reload()
}
