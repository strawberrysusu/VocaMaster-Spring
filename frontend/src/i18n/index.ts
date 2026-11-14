// 화면 문구 번역 — 다국어 (11/14, ADR-056)
//
// · 한국어 문구 자체가 열쇠다: t('채점하기') → 고른 언어의 문구. 한국어 화면은 열쇠를 그대로 보여 주니 예전과 똑같다.
// · 일본어·영어는 사전(ja.ts·en.ts)에서 찾는다. 두 사전은 열쇠가 똑같아야 한다 —
//   en의 타입이 Messages(= ja의 열쇠 목록)라서 빠지거나 남는 열쇠는 tsc가 잡는다. t()에 사전에 없는 열쇠를 넣어도 tsc가 잡는다.
// · {n} 같은 자리는 vars로 채운다: t('틀린 {n}개만 다시 풀기', { n: 3 })
// · 언어는 설정(브라우저 저장 vm.settings.lang)에서 고르고, 없으면 브라우저 언어를 따른다(일본어·영어가 아니면 한국어).
//   바꾸면 새로고침한다 — 파일이 처음 불릴 때 정해진 문구까지 새 언어로 바뀌게.
import { ja, type Messages } from './ja'
import { en } from './en'

export type Lang = 'ko' | 'ja' | 'en'
export type Key = keyof Messages

/** 언어 고르기 목록 — 이름은 그 언어로 적는다(자기 언어를 찾기 쉽게) */
export const LANGS: { code: Lang; label: string }[] = [
  { code: 'ko', label: '한국어' },
  { code: 'ja', label: '日本語' },
  { code: 'en', label: 'English' },
]

const DICTS: Record<Exclude<Lang, 'ko'>, Messages> = { ja, en }
const SETTINGS_KEY = 'vm.settings' // lib/settings.ts와 같은 저장 칸

function isLang(v: unknown): v is Lang {
  return v === 'ko' || v === 'ja' || v === 'en'
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
  return 'ko'
}

let current: Lang = detectLang()
if (typeof document !== 'undefined') document.documentElement.lang = current

export function getLang(): Lang {
  return current
}

/** 고른 언어의 문구. 사전에 없으면(있을 수 없지만) 한국어 그대로 */
export function t(key: Key, vars?: Record<string, string | number>): string {
  const text: string = current === 'ko' ? key : (DICTS[current][key] ?? key)
  if (!vars) return text
  return text.replace(/\{(\w+)\}/g, (whole, name: string) => (name in vars ? String(vars[name]) : whole))
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
