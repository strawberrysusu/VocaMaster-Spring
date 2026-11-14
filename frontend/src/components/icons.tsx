import type { ReactNode } from 'react'

// 줄 아이콘 모음 (의존성 없이 인라인 SVG) — 위쪽 바·사이드바(TopNav)와 덱 화면 학습 버튼이 같이 쓴다.
// 글자 옆 장식이라 aria-hidden — 버튼 이름은 옆 글자가 된다 (E2E도 글자로 버튼을 찾는다)
const svg = (size: number, children: ReactNode) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {children}
  </svg>
)

export const ICON = {
  // 메뉴
  home: svg(18, <><path d="M3 10.5 12 3l9 7.5" /><path d="M5.5 9.5V21h13V9.5" /></>),
  decks: svg(18, <><rect x="4" y="7" width="14" height="13" rx="2.5" /><path d="M8 4h11a2 2 0 0 1 2 2v11" /></>),
  explore: svg(18, <><circle cx="12" cy="12" r="8.5" /><path d="m15.5 8.5-2 5-5 2 2-5z" /></>),
  stats: svg(18, <><path d="M5 20V13" /><path d="M12 20V5" /><path d="M19 20v-9" /></>),
  settings: svg(18, <><path d="M4 8h10" /><circle cx="17" cy="8" r="2.5" /><path d="M20 16H10" /><circle cx="7" cy="16" r="2.5" /></>),
  folder: svg(16, <path d="M3.5 6.5a2 2 0 0 1 2-2h4l2 2.5h7a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z" />),
  // 위쪽 바
  search: svg(18, <><circle cx="11" cy="11" r="7" /><path d="m20 20-3.8-3.8" /></>),
  plus: svg(20, <><path d="M12 5v14" /><path d="M5 12h14" /></>),
  file: svg(18, <><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" /><path d="M14 3v5h5" /></>),
  shield: svg(18, <path d="M12 3 5 6v6c0 4.2 3 7.6 7 9 4-1.4 7-4.8 7-9V6z" />),
  logout: svg(18, <><path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3" /><path d="M10 16l-4-4 4-4" /><path d="M6 12h10" /></>),
  // 덱 화면 학습 버튼 (11/14 사용자 요청 — 글자만으론 구분이 어렵다)
  cards: svg(18, <><rect x="3.5" y="7" width="13" height="13" rx="2.5" /><path d="M7.5 4h10.5a2 2 0 0 1 2 2v10.5" /></>),
  review: svg(18, <><path d="M20 11.5a8 8 0 1 1-2.4-5.7" /><path d="M20 4v5h-5" /></>),
  star: svg(18, <path d="m12 3.5 2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8L3.5 9.7l5.9-.9z" />),
  quiz: svg(18, <><circle cx="12" cy="12" r="8.5" /><path d="M9.6 9.6a2.5 2.5 0 1 1 3.4 2.3c-.6.3-1 .8-1 1.5v.4" /><path d="M12 17h.01" /></>),
  keyboard: svg(18, <><rect x="2.5" y="6" width="19" height="12" rx="2" /><path d="M6.5 10h.01M10 10h.01M14 10h.01M17.5 10h.01" /><path d="M7.5 14h9" /></>),
  headphones: svg(18, <><path d="M4 15v-3a8 8 0 0 1 16 0v3" /><rect x="3" y="14" width="4" height="6" rx="1.5" /><rect x="17" y="14" width="4" height="6" rx="1.5" /></>),
  sheet: svg(18, <><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" /><path d="M14 3v5h5" /><path d="M9 13h6" /><path d="M9 17h4" /></>),
  chevronDown: svg(16, <path d="m6 9 6 6 6-6" />),
}
