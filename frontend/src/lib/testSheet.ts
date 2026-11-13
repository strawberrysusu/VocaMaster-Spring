// 종이 시험지 — 덱 카드로 "단어 → (읽기) · 뜻" 쓰기 시험 문항을 만든다 (2단계, 11/14).
// 사용자 양식(A4, 3칸, 칸 높이 1.8cm) 그대로 Word 파일로 내려받아 인쇄 → 손으로 쓰고 → 정답지로 직접 채점.
//  · 일본어 덱: 번호 | 단어 | 읽기 | 뜻. 한자가 없는 단어(가나만)는 읽기 칸을 회색으로 비워 둔다.
//  · 영어 덱(일본어가 하나도 없는 덱): 번호 | 단어 | 뜻
// 화면·서버와 무관한 순수 함수 — scripts/test-test-sheet.mjs 가 검사한다.
import type { CardDto } from '../api/cards'

export type SheetScope = 'all' | 'starred'

export interface SheetOptions {
  scope: SheetScope
  /** 문제 수 — 'all'이면 범위 안의 카드 전부 */
  count: number | 'all'
  shuffle: boolean
}

export interface SheetRow {
  no: number
  word: string
  /** 정답지용 읽기 (없으면 빈 문자열) */
  reading: string
  /** 정답지용 뜻 */
  meaning: string
  /** 읽기를 쓰는 문항인가 — 한자가 있는 일본어 단어만 true */
  needsReading: boolean
}

export interface SheetModel {
  /** 읽기 칸이 있는 일본어 양식인가 */
  japanese: boolean
  rows: SheetRow[]
}

type SheetCard = Pick<CardDto, 'front' | 'back' | 'reading' | 'starred'>

// 한자: CJK 통합 한자 + 확장 A + 호환 한자 + 々〆ヶ
const KANJI = /[㐀-䶿一-鿿豈-﫿々〆ヶ]/
const KANA = /[぀-ゟ゠-ヿ]/

export function hasKanji(s: string): boolean {
  return KANJI.test(s)
}

function isJapanese(s: string): boolean {
  return KANJI.test(s) || KANA.test(s)
}

/** 범위에 들어가는 카드 수 (문제 수 고르기 화면용) */
export function poolSize(cards: SheetCard[], scope: SheetScope): number {
  return scope === 'starred' ? cards.filter((c) => c.starred).length : cards.length
}

/**
 * 시험지 문항 만들기. random은 테스트에서 고정값을 넣으려고 받는다.
 * 순서 섞기는 Fisher–Yates — 정답지는 같은 rows를 쓰니 문제지와 번호가 항상 같다.
 */
export function buildSheet(cards: SheetCard[], opts: SheetOptions, random: () => number = Math.random): SheetModel {
  const pool = opts.scope === 'starred' ? cards.filter((c) => c.starred) : [...cards]
  if (opts.shuffle) {
    for (let i = pool.length - 1; i > 0; i--) {
      const j = Math.floor(random() * (i + 1))
      ;[pool[i], pool[j]] = [pool[j], pool[i]]
    }
  }
  const picked = opts.count === 'all' ? pool : pool.slice(0, Math.max(0, opts.count))
  const japanese = picked.some((c) => isJapanese(c.front))
  return {
    japanese,
    rows: picked.map((c, i) => ({
      no: i + 1,
      word: c.front.trim(),
      reading: (c.reading ?? '').trim(),
      meaning: c.back.trim(),
      needsReading: japanese && hasKanji(c.front),
    })),
  }
}

/** 예상 쪽수 — 문제지는 칸 높이 1.8cm라 A4 한 쪽에 12줄쯤(첫 쪽은 제목 때문에 11줄), 정답지는 26줄쯤 (Word 실측: 30문항 = 3쪽 + 2쪽) */
export function estimatePages(rowCount: number, withAnswerKey: boolean): { sheet: number; answerKey: number } {
  if (rowCount <= 0) return { sheet: 0, answerKey: 0 }
  const sheet = rowCount <= 11 ? 1 : 1 + Math.ceil((rowCount - 11) / 12)
  const answerKey = withAnswerKey ? Math.ceil(rowCount / 26) : 0
  return { sheet, answerKey }
}

/** Windows 파일 이름에 못 쓰는 글자를 지운 "덱이름_시험지.docx" */
export function sheetFileName(deckTitle: string): string {
  // eslint-disable-next-line no-control-regex -- 파일 이름에 못 쓰는 제어 문자를 일부러 지운다
  const base = deckTitle.replace(/[\\/:*?"<>|\u0000-\u001F]/g, ' ').replace(/\s+/g, ' ').trim() || '단어장'
  return `${base}_시험지.docx`
}
