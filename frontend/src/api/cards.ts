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

// ── 진행 중인 별표 쓰기 (Codex 9차 검산 11/18) ─────────────────────────────────────────────
// 카드 목록은 화면에 들어올 때 한 번만 읽는다. 별표를 바꾸는 요청이 서버에 닿기 전에 같은 덱의 목록을 읽으면
// 바뀌기 전 별표가 화면에 남는다 — 온라인 시험 채점 직후 저장 중에 복습·덱 화면으로 가거나, 덱 화면에서
// 별표 요청과 목록 다시 읽기(카드 추가 등)가 겹칠 때 실제로 그랬다.
// 그래서 별표 쓰기를 덱별로 적어 두고, 목록 읽기(fetchAllCards)는 그것이 끝난 뒤에 출발한다.
const starWrites = new Map<string, Set<Promise<void>>>()
/** 목록 읽기가 별표 쓰기를 기다리는 최대 시간 — 응답이 영영 안 오는 요청 하나 때문에 목록이 안 뜨는 일은 없게 */
const STAR_WRITE_WAIT_MS = 8000

/**
 * 별표를 바꾸는 작업을 적어 둔다 — 성공이든 실패든 끝날 때까지 같은 덱의 목록 읽기가 기다린다.
 * 요청 하나가 아니라 '작업' 단위로 적을 수 있다: 온라인 시험 채점은 켜기 → 끄기 두 요청이 이어지는데,
 * 요청마다 따로 적으면 두 요청 사이의 빈틈에 목록 읽기가 끼어든다 (OnlineTest.syncStars가 통째로 적는다).
 */
export function trackStarWrite<T>(deckId: string | number, work: Promise<T>): Promise<T> {
  const key = String(deckId)
  const writes = starWrites.get(key) ?? new Set<Promise<void>>()
  starWrites.set(key, writes)
  const settled = work.then(
    () => undefined,
    () => undefined,
  )
  writes.add(settled)
  void settled.then(() => {
    writes.delete(settled)
    if (writes.size === 0 && starWrites.get(key) === writes) starWrites.delete(key)
  })
  return work
}

/** 그 덱의 별표 쓰기가 모두 끝날 때까지 기다린다 — 기다리는 사이 새로 시작된 것까지, 길어도 STAR_WRITE_WAIT_MS */
async function starWritesSettled(deckId: string | number): Promise<void> {
  const deadline = Date.now() + STAR_WRITE_WAIT_MS
  for (;;) {
    const writes = starWrites.get(String(deckId))
    const left = deadline - Date.now()
    if (!writes || writes.size === 0 || left <= 0) return
    let timer: ReturnType<typeof setTimeout> | undefined
    await Promise.race([
      Promise.all([...writes]),
      new Promise<void>((resolve) => {
        timer = setTimeout(resolve, left)
      }),
    ])
    clearTimeout(timer)
  }
}

export async function fetchAllCards(deckId: string | number): Promise<{ cards: CardDto[]; total: number }> {
  await starWritesSettled(deckId) // 진행 중인 별표 쓰기가 끝난 뒤에 읽는다 — 먼저 읽으면 바뀌기 전 별표가 화면에 남는다
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
  /** 건너뛴 카드 — 이 덱에 없는 카드(시험 도중 지워졌거나 다른 덱의 카드). 값을 안 바꾼다 */
  skippedIds: number[]
}

/** 서버 별표 일괄 API가 한 번에 받는 카드 수 — SetStarredRequest의 @Size(max = 500)과 같아야 한다 */
export const STAR_BATCH = 500

/**
 * 별표 일괄 설정 (멱등, ADR-053) — 온라인 시험지 오답 자동 ⭐ / '맞음으로' 되돌리기.
 * 500장씩 나눠 차례로 보낸다 ('전체' 시험은 문항 수 제한이 없어 오답이 500개를 넘을 수 있다 — Codex 검산 11/14).
 * 중간 묶음이 실패하면 앞 묶음은 반영된 채로 오류를 던진다 — 값 지정이라 처음부터 다시 보내도 결과가 같다.
 * (그때 앞 묶음의 changedIds는 호출한 쪽에 전해지지 않는다 — 다시 보내면 그 카드들은 "바뀐 것 없음"으로 온다.
 *  바뀐 수를 세는 쪽은 이것을 감안해야 한다: OnlineTest의 offUnsure)
 * 끝날 때까지 같은 덱의 목록 읽기가 기다린다 (trackStarWrite).
 */
export function setStarred(deckId: string | number, cardIds: number[], starred: boolean): Promise<SetStarredResult> {
  return trackStarWrite(deckId, sendStarred(deckId, cardIds, starred))
}

async function sendStarred(deckId: string | number, cardIds: number[], starred: boolean): Promise<SetStarredResult> {
  const changedIds: number[] = []
  const skippedIds: number[] = []
  for (let start = 0; start < cardIds.length; start += STAR_BATCH) {
    const r = await api<SetStarredResult>(`/decks/${deckId}/cards/star`, {
      method: 'PATCH',
      body: JSON.stringify({ cardIds: cardIds.slice(start, start + STAR_BATCH), starred }),
    })
    changedIds.push(...r.changedIds)
    skippedIds.push(...(r.skippedIds ?? []))
  }
  return { starred, changedIds, skippedIds }
}

/**
 * 카드 한 장의 별표 뒤집기 (덱 화면·낱말카드의 ★ 단추). 누를 때마다 값이 뒤집히는 요청이라 같은 카드에 둘을 겹쳐 보내면 안 된다 —
 * 부르는 쪽이 응답 전의 두 번째 누름을 막는다. deckId를 주면 그 덱의 목록 읽기가 이 요청이 끝난 뒤에 출발한다.
 */
export function toggleCardStar(deckId: string | number | null | undefined, cardId: number): Promise<{ starred: boolean }> {
  const request = api<{ starred: boolean }>(`/cards/${cardId}/star`, { method: 'PATCH' })
  return deckId == null ? request : trackStarWrite(deckId, request)
}
