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
  /** 온라인 시험지가 틀린 카드를 ⭐ 할 때 쓴다 */
  cardId: number
  word: string
  /** 정답지용 읽기 (없으면 빈 문자열) */
  reading: string
  /** 정답지용 뜻 */
  meaning: string
  /** 설명 줄 (없으면 빈 문자열) — 정답지·온라인 채점 결과에 뜻 아래 작은 글씨로 */
  memo: string
  /** 읽기를 쓰는 문항인가 — 한자가 있는 일본어 단어만 true */
  needsReading: boolean
}

export interface SheetModel {
  /** 읽기 칸이 있는 일본어 양식인가 */
  japanese: boolean
  rows: SheetRow[]
}

type SheetCard = Pick<CardDto, 'id' | 'front' | 'back' | 'reading' | 'memo' | 'starred'>

// 한자: CJK 통합 한자 4E00–9FFF + 확장 A 3400–4DBF + 호환 한자 F900–FAFF + BMP 밖 확장 B~ 20000–3FFFF
// (𠮟 U+20B9F는 2010년부터 상용한자 표기) + 々〆ヶ (Codex 검산 11/14)
// · u 플래그: 없으면 BMP 밖 글자를 반쪽(서로게이트) 둘로 봐서 못 찾는다
// · \u 표기: 글자를 그대로 쓰면 편집기·도구의 NFC 정규화가 호환 한자 豈(F900)를 8C48로 바꿔 범위가 한글까지 넓어진다
const KANJI = /[\u3400-\u4DBF\u4E00-\u9FFF\uF900-\uFAFF\u{20000}-\u{3FFFF}\u3005\u3006\u30F6]/u
// 가나: 히라가나 3040–309F + 가타카나 30A0–30FF
const KANA = /[\u3040-\u309F\u30A0-\u30FF]/

export function hasKanji(s: string): boolean {
  return KANJI.test(s)
}

/** 괄호 속 문맥을 뺀 말 — '(話が)こじれる'에서 시험 보는 낱말은 こじれる다 (11/14 검토: 문맥의 한자 때문에 가나 낱말에 읽기 칸이 생겼다) */
function withoutContext(s: string): string {
  return s.replace(/[(（][^()（）]*[)）]/g, '')
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
      cardId: c.id,
      word: c.front.trim(),
      reading: (c.reading ?? '').trim(),
      meaning: c.back.trim(),
      memo: (c.memo ?? '').trim(),
      needsReading: japanese && hasKanji(withoutContext(c.front)),
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

/**
 * 파일 이름에 들어가는 글자 — 기본은 한국어. 화면(TestSheetDialog)이 고른 언어의 문구를 넘긴다.
 * 이 파일은 i18n을 import 하지 않는다: scripts/load-ts.mjs가 상대 경로 import를 못 풀어서 (다국어 11/14)
 */
export interface SheetFileLabels {
  /** 덱 이름이 비었을 때 대신 쓰는 이름 */
  fallback: string
  /** 정리한 덱 이름 → 파일 이름 */
  fileName: (base: string) => string
}

export const SHEET_FILE_LABELS_KO: SheetFileLabels = {
  fallback: '단어장',
  fileName: (base) => `${base}_시험지.docx`,
}

/** Windows 파일 이름에 못 쓰는 글자를 지운 "덱이름_시험지.docx" */
export function sheetFileName(deckTitle: string, labels: SheetFileLabels = SHEET_FILE_LABELS_KO): string {
  // eslint-disable-next-line no-control-regex -- 파일 이름에 못 쓰는 제어 문자를 일부러 지운다
  const base = deckTitle.replace(/[\\/:*?"<>|\u0000-\u001F]/g, ' ').replace(/\s+/g, ' ').trim() || labels.fallback
  return labels.fileName(base)
}
