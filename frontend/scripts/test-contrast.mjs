// 글자 대비 검사 (ADR-066) — src/index.css에서 글자색(color)을 정한 규칙마다 배경과의 대비를 잰다.
// 기준(WCAG 2 AA): 보통 글자 4.5:1, 큰 글자(24px 이상, 또는 18.66px 이상 굵게) 3:1.
// 배경: 같은 규칙에 단색 background가 있으면 그것, 없으면 페이지 배경 --bg(#fafafb) — 흰 카드(#fff)보다 엄격한 쪽.
// 11/25 운영 Lighthouse가 로그인 버튼의 연회색(3.35:1)을 찾은 뒤, 같은 문제가 다시 들어오지 않게 넣었다.
//   npm test --prefix frontend
import { readFileSync } from 'node:fs'

const css = readFileSync(new URL('../src/index.css', import.meta.url), 'utf8')

// 기준에서 빠지는 규칙 — 이유와 함께. 새로 넣을 때도 이유를 적는다
const EXEMPT = new Map([
  ['.cta.disabled', '비활성 버튼 (WCAG 예외)'],
  ['.btn-primary:disabled', '비활성 버튼 (WCAG 예외)'],
  ['.mode-stub', '비활성 학습 버튼 — 카드가 모자랄 때 (WCAG 예외)'],
  ['.sheet-choice.off', '시험지 창의 비활성 선택지 (WCAG 예외)'],
  ['.top-menu .menu-disabled', '비활성 메뉴 항목 (WCAG 예외)'],
  ['.nav-links a.soon', '쓰는 곳 없는 옛 메뉴 규칙'],
  ['.folder-row-arrow', '장식 기호 › (뜻은 줄 전체가 링크라 전해진다)'],
  ['.bulk-sep', '장식 기호 → · (일괄 이동 줄의 구분)'],
  ['.otest-noread', '장식 기호 — (읽기를 묻지 않는 칸, title로 설명)'],
])
// 글자가 아니라 그림(아이콘)인 규칙 — 기준이 3:1 (WCAG 1.4.11)
const ICON = new Map([['.star-btn.on', '덱 화면의 별 아이콘 ★']])

const hex6 = (h) => {
  let s = h.replace('#', '')
  if (s.length === 3) s = [...s].map((c) => c + c).join('')
  return s.length === 6 ? s : null
}
const lum = (h) => {
  const s = hex6(h)
  const c = [0, 2, 4].map((i) => parseInt(s.slice(i, i + 2), 16) / 255).map((v) => (v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4))
  return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
}
const ratio = (a, b) => {
  const x = lum(a)
  const y = lum(b)
  return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05)
}

// :root의 색 변수
const vars = {}
for (const m of css.matchAll(/--([\w-]+):\s*(#[0-9a-fA-F]{3,6})\b/g)) vars[m[1]] = m[2]
const BG = vars.bg ?? '#fafafb'

/** "#abc" · "var(--x)" · "var(--x, #abc)" → 단색 hex, 모르면 null */
function solid(value) {
  const v = value.trim()
  const hex = v.match(/^#[0-9a-fA-F]{3,6}$/)
  if (hex && hex6(v)) return v
  const ref = v.match(/^var\(--([\w-]+)(?:\s*,\s*(#[0-9a-fA-F]{3,6}))?\)$/)
  if (ref) return vars[ref[1]] ?? ref[2] ?? null
  return null
}

const failures = []
let checked = 0
const body = css.replace(/\/\*[\s\S]*?\*\//g, '') // 주석은 빼고 본다
for (const [, rawSelector, decls] of body.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
  const selector = rawSelector.trim().replace(/\s+/g, ' ')
  if (selector.startsWith('@') || selector.includes('@media')) continue
  const color = decls.match(/(?:^|[;\s])color:\s*([^;]+)/)
  if (!color) continue
  const fg = solid(color[1])
  if (!fg) continue
  const bgDecl = decls.match(/(?:^|[;\s])background(?:-color)?:\s*([^;]+)/)
  const bg = (bgDecl && solid(bgDecl[1])) || BG
  const size = Number((decls.match(/font-size:\s*([\d.]+)px/) ?? [])[1] ?? 0)
  const bold = /font-weight:\s*(700|800|900|bold)/.test(decls)
  const large = size >= 24 || (size >= 18.66 && bold)
  const need = large || ICON.has(selector) ? 3 : 4.5
  checked++
  if (EXEMPT.has(selector)) continue
  const r = ratio(fg, bg)
  if (r < need) failures.push(`${selector} — 글자 ${fg} / 배경 ${bg} = ${r.toFixed(2)}:1 (기준 ${need}:1)`)
}

if (failures.length) {
  console.error('글자 대비가 기준에 못 미치는 규칙:\n  ' + failures.join('\n  '))
  console.error(`\n${failures.length}개 실패 — 색을 진하게 하거나(--muted·--muted-2 등), 정말 예외면 EXEMPT에 이유와 함께 적는다`)
  process.exit(1)
}
console.log(`contrast: 글자색 규칙 ${checked}개 검사 — 기준 미달 없음 (예외 ${EXEMPT.size}개)`)
