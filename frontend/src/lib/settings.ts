// 사용자 설정 — 브라우저 저장(localStorage). 서버 저장은 "기기 간 동기화"가 필요해질 때 (백로그).
// 목업의 스위치(accent·deckColumns·quizAutoAdvance·showReading)를 실물로 만든 것 (2026-08-23).

export type AccentKey = 'indigo' | 'teal' | 'navy' | 'purple'

export const ACCENTS: Record<AccentKey, { label: string; a: string; soft: string }> = {
  indigo: { label: '인디고', a: '#3d3e96', soft: '#ececf7' },
  teal: { label: '틸', a: '#2f8f83', soft: '#e6f3f1' },
  navy: { label: '네이비', a: '#2b4a8a', soft: '#e8edf6' },
  purple: { label: '퍼플', a: '#6b46a8', soft: '#efe9f7' },
}

export interface Settings {
  accent: AccentKey
  deckColumns: 2 | 3 | 4
  quizAutoAdvance: boolean
  quizChoices: 4 | 5 | 6         // 퀴즈 선택지 수 (한국 시험 스타일 5지, 최대 6지)
  voices: Partial<Record<'en' | 'ja' | 'ko', string>>   // 언어별 선호 음성 이름 (없으면 tts.ts 우선순위 규칙)
}

const KEY = 'vm.settings'

const DEFAULTS: Settings = { accent: 'indigo', deckColumns: 3, quizAutoAdvance: false, quizChoices: 4, voices: {} }

export function loadSettings(): Settings {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? { ...DEFAULTS, ...JSON.parse(raw) } : { ...DEFAULTS }
  } catch {
    return { ...DEFAULTS }
  }
}

export function saveSettings(next: Settings): void {
  localStorage.setItem(KEY, JSON.stringify(next))
  applySettings(next)
}

// CSS 변수로 적용 — 디자인 토큰이 --a 하나로 모여 있어 색 교체가 변수 2개로 끝난다
export function applySettings(s: Settings = loadSettings()): void {
  const root = document.documentElement
  const accent = ACCENTS[s.accent] ?? ACCENTS.indigo
  root.style.setProperty('--a', accent.a)
  root.style.setProperty('--a-soft', accent.soft)
  root.style.setProperty('--deck-cols', String(s.deckColumns))
}

export function voicePreference(lang: string): string | undefined {
  const base = lang.slice(0, 2) as 'en' | 'ja' | 'ko'
  return loadSettings().voices[base]
}
