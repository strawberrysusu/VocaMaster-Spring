import { api } from './client'

export interface CardDto {
  id: number
  front: string
  back: string
  reading?: string | null   // 읽기(요미가나) — 없으면 null
  starred: boolean
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
