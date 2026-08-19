import { api } from './client'

export interface PublicDeck {
  id: number
  title: string
  description: string
  authorNickname: string
  cardCount: number
  likeCount: number
  copyCount: number
  likedByMe: boolean   // 서버가 계산 — 로그인 사용자 기준, 익명 false
  mine: boolean        // 내 덱이면 true — 자기 복사는 copy_count에 안 오름
}

export interface LikeResponse {
  liked: boolean
  likeCount: number
}

export interface PageResp<T> {
  content: T[]
  totalElements: number
}

export interface PublicCard {
  id: number
  front: string
  back: string
}

export function toggleLikeApi(deck: PublicDeck) {
  return api<LikeResponse>(`/public/decks/${deck.id}/like`, { method: deck.likedByMe ? 'DELETE' : 'POST' })
}

export function copyDeckApi(deckId: number) {
  return api<{ id: number }>(`/decks/${deckId}/copy`, { method: 'POST' })
}

// 서버가 자기 복사는 카운트에서 제외 — UI도 동일하게 (mine이면 +1 안 함)
export function afterCopy(d: PublicDeck): PublicDeck {
  return d.mine ? d : { ...d, copyCount: d.copyCount + 1 }
}
