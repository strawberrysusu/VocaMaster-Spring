import fs from 'node:fs'
import { test as base, expect, type APIRequestContext, type APIResponse, type Page } from '@playwright/test'
import { AUTH_FILE } from '../paths'

export { expect }

export interface Card {
  id: number
  front: string
  back: string
  reading: string | null
  memo: string | null
  starred: boolean
}

// 모든 테스트 공통: 브라우저 오류(잡히지 않은 예외·console.error)가 하나라도 나면 실패.
// 일부러 요청을 끊는 테스트는 그 직후 errors 배열을 비운다
export const test = base.extend<{ errors: string[] }>({
  errors: [
    async ({ page }, use) => {
      const errors: string[] = []
      page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`))
      page.on('console', (m) => {
        if (m.type() === 'error') errors.push(`console.error: ${m.text()}`)
      })
      await use(errors)
      expect(errors, '브라우저 오류').toEqual([])
    },
    { auto: true },
  ],
})

function accessToken(): string {
  const state = JSON.parse(fs.readFileSync(AUTH_FILE, 'utf8')) as {
    origins: { localStorage: { name: string; value: string }[] }[]
  }
  for (const origin of state.origins)
    for (const item of origin.localStorage) if (item.name === 'vm.accessToken') return item.value
  throw new Error('access token이 없어요 — setup 프로젝트(auth.setup.ts)가 먼저 돌아야 해요')
}
const auth = () => ({ Authorization: `Bearer ${accessToken()}` })

async function ok<T>(res: APIResponse): Promise<T> {
  if (!res.ok()) throw new Error(`${res.status()} ${res.url()} — ${await res.text()}`)
  return (await res.json()) as T
}

// 제목은 실제 덱처럼 띄어쓰기로 끊기게 (띄어쓰기 없는 긴 제목은 layout.spec.ts가 따로 본다)
let seq = 0
const deckTitle = (label: string) => `E2E ${label} ${++seq} ${Date.now() % 1_000_000}`

/** txt 가져오기와 같은 경로로 덱 만들기 — 한 줄에 '단어|뜻' 또는 '단어|읽기|뜻' */
export async function makeDeck(request: APIRequestContext, label: string, lines: string[]): Promise<number> {
  const body = await ok<{ deckId: number; imported: number }>(
    await request.post('/decks/import-file', {
      headers: auth(),
      data: { title: deckTitle(label), text: lines.join('\n') },
    }),
  )
  expect(body.imported, '가져온 카드 수').toBe(lines.length)
  return body.deckId
}

export async function makeEmptyDeck(request: APIRequestContext, label: string): Promise<number> {
  const body = await ok<{ id: number }>(await request.post('/decks', { headers: auth(), data: { title: deckTitle(label) } }))
  return body.id
}

export async function addCard(
  request: APIRequestContext,
  deckId: number,
  card: { front: string; back: string; reading?: string; memo?: string },
): Promise<Card> {
  return ok<Card>(await request.post(`/decks/${deckId}/cards`, { headers: auth(), data: card }))
}

export async function listCards(request: APIRequestContext, deckId: number): Promise<Card[]> {
  const out: Card[] = []
  for (let page = 0; ; page++) {
    const body = await ok<{ content: Card[]; totalElements: number }>(
      await request.get(`/decks/${deckId}/cards?size=100&page=${page}`, { headers: auth() }),
    )
    out.push(...body.content)
    if (body.content.length === 0 || out.length >= body.totalElements) return out
  }
}

export async function isStarred(request: APIRequestContext, deckId: number, front: string): Promise<boolean> {
  const card = (await listCards(request, deckId)).find((c) => c.front === front)
  if (!card) throw new Error(`카드가 없어요: ${front}`)
  return card.starred
}

export async function toggleStar(request: APIRequestContext, cardId: number): Promise<void> {
  await ok(await request.patch(`/cards/${cardId}/star`, { headers: auth() }))
}

/** 카드 지우기 — 다른 탭·기기에서 지운 것처럼 (응답 본문이 없어 상태만 본다) */
export async function deleteCard(request: APIRequestContext, cardId: number): Promise<void> {
  const res = await request.delete(`/cards/${cardId}`, { headers: auth() })
  if (!res.ok()) throw new Error(`${res.status()} ${res.url()} — ${await res.text()}`)
}

/** 가로 스크롤 크기(px) — 0이어야 정상 */
export function overflowX(page: Page): Promise<number> {
  return page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
}

// 실제 단어장에서 가장 긴 조합에 가까운 문장 (폰 화면 넘침 점검용)
export const LONG = {
  word: '三段リーグ',
  reading: 'さんだんリーグ',
  back: '프로 기사 승단을 걸고 반년마다 치르는 장기 리그전',
  memo: '상위 두 명만 4단으로 올라가고 나이 제한이 있어, 많은 지망생이 여기서 꿈을 접는다',
  memo64: '본디 나무나 종이 조각에 글을 적은 것을 가리키며, 표·번호표·카드·지폐처럼 무언가를 나타내는 작은 조각을 두루 이른다',
}
