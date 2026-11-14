import { api } from './client'

export type LearningStatus = 'UNKNOWN' | 'KNOWN'

export interface CardDto {
  id: number
  front: string
  back: string
  reading?: string | null   // 읽기(요미가나) — 없으면 null
  memo?: string | null      // 설명 줄 — 뜻 아래 작은 글씨 (11/14 4단계), 없으면 null
  starred: boolean
  learningStatus: LearningStatus
  correctStreak: number
  wrongCount: number
}

interface PageResp {
  content: CardDto[]
  totalElements: number
}

// 서버 PageableUtils 상한과 일치 — size=200을 보내도 100으로 잘리므로 '전 페이지 순회'가 정답.
// (예전 size=200 편법은 101장째부터 조용히 누락됐다 — Codex 검산)
const PAGE_SIZE = 100

export async function fetchAllCards(deckId: string | number): Promise<{ cards: CardDto[]; total: number }> {
  const first = await api<PageResp>(`/decks/${deckId}/cards?size=${PAGE_SIZE}&page=0`)
  const cards = [...first.content]
  const totalPages = Math.ceil(first.totalElements / PAGE_SIZE)
  for (let page = 1; page < totalPages; page++) {
    const next = await api<PageResp>(`/decks/${deckId}/cards?size=${PAGE_SIZE}&page=${page}`)
    cards.push(...next.content)
  }
  return { cards, total: first.totalElements }
}

export interface SetStarredResult {
  starred: boolean
  /** 이번 요청으로 값이 실제로 바뀐 카드 (이미 그 값이던 카드는 빠진다) */
  changedIds: number[]
}

/** 별표 일괄 설정 (멱등, ADR-053) — 온라인 시험지 오답 자동 ⭐ / '맞음으로' 되돌리기 */
export function setStarred(deckId: string | number, cardIds: number[], starred: boolean): Promise<SetStarredResult> {
  return api<SetStarredResult>(`/decks/${deckId}/cards/star`, {
    method: 'PATCH',
    body: JSON.stringify({ cardIds, starred }),
  })
}
