import type { Page } from '@playwright/test'
import { test, expect, makeDeck, listCards, isStarred, toggleStar, overflowX, LONG, makeEmptyDeck, addCard } from './support'

// 온라인 시험지(ADR-053): 채점하면 틀리거나 비운 단어는 자동 ⭐, '맞음으로'는 이 시험이 켠 ⭐만 끈다

const open = (page: Page, deckId: number) => page.goto(`/app/test/${deckId}?scope=all&count=all&shuffle=0`)
const row = (page: Page, word: string) =>
  page.locator('.otest-row').filter({ has: page.locator('.otest-word', { hasText: word }) })
const summary = (page: Page) => page.locator('.otest-summary')
const grade = (page: Page) => page.getByRole('button', { name: /채점하기/ }).click()
const STAR_API = '**/decks/*/cards/star'

test('채점: 틀리거나 비운 단어만 ⭐, 맞음으로는 이 시험이 켠 ⭐만 끈다', async ({ page, request }) => {
  const deckId = await makeDeck(request, '온라인', ['感謝|かんしゃ|감사', '会議|かいぎ|회의', 'ジャンケン|가위바위보'])
  const janken = (await listCards(request, deckId)).find((c) => c.front === 'ジャンケン')!
  await toggleStar(request, janken.id) // 시험 전부터 ⭐였던 카드

  await open(page, deckId)
  await row(page, '感謝').getByLabel(/읽기$/).fill('カンシャ') // 가타카나로 써도 정답
  await row(page, '感謝').getByLabel(/뜻$/).fill('감사')
  await row(page, 'ジャンケン').getByLabel(/뜻$/).fill('보') // 오답
  // 会議는 비워 둔다 = 모름
  await grade(page)

  await expect(summary(page)).toContainText('점수 1/3')
  await expect(summary(page)).toContainText('⭐ 완료')
  expect(await isStarred(request, deckId, '会議')).toBe(true)
  expect(await isStarred(request, deckId, 'ジャンケン')).toBe(true)
  expect(await isStarred(request, deckId, '感謝')).toBe(false)

  await row(page, '会議').getByRole('button', { name: '맞음으로' }).click()
  await expect(row(page, '会議').getByRole('button', { name: '다시 오답으로' })).toBeVisible()
  await row(page, 'ジャンケン').getByRole('button', { name: '맞음으로' }).click()
  await expect(row(page, 'ジャンケン').getByRole('button', { name: '다시 오답으로' })).toBeVisible()
  expect(await isStarred(request, deckId, '会議')).toBe(false) // 이 시험이 켠 ⭐ → 끔
  expect(await isStarred(request, deckId, 'ジャンケン')).toBe(true) // 원래 ⭐ → 그대로

  await row(page, '会議').getByRole('button', { name: '다시 오답으로' }).click()
  await expect(row(page, '会議').getByRole('button', { name: '맞음으로' })).toBeVisible()
  expect(await isStarred(request, deckId, '会議')).toBe(true)
})

test('폰 화면: 채점 뒤 정답·설명 줄이 붙어도 가로로 넘치지 않는다', async ({ page, request }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  const deckId = await makeEmptyDeck(request, '온라인폰')
  await addCard(request, deckId, { front: LONG.word, reading: LONG.reading, back: LONG.back, memo: LONG.memo })
  await addCard(request, deckId, { front: 'ざら', back: '아주 흔함' })
  await open(page, deckId)
  await expect(page.locator('.otest-row')).toHaveCount(2)
  expect(await overflowX(page)).toBe(0)
  await grade(page)
  await expect(summary(page)).toContainText('⭐ 완료')
  await expect(page.locator('.otest-memo')).toBeVisible()
  expect(await overflowX(page)).toBe(0)
})

// ── 회귀: Codex 검산(11/14)이 찾은 3건. PR #26에서 test.fail로 고치기 전 동작을 재현했고(3건 모두 실패 확인),
//    PR #27에서 고친 뒤 표시를 뗐다 — 이제 다시 생기면 이 테스트가 막는다
test.describe('회귀 — 온라인 시험 별표 (Codex 검산 11/14)', () => {
  // 고치기 전: 저장 중에도 눌려서, 늦게 온 응답이 다음 시험의 같은 번호(다른 단어)를 정답으로 바꿨다
  test('맞음으로 저장이 끝나기 전에는 "다시 풀기"가 막혀 있다', async ({ page, request }) => {
    const deckId = await makeDeck(request, '경합', ['感謝|かんしゃ|감사', '会議|かいぎ|회의', '運輸|うんゆ|운수'])
    await open(page, deckId)
    await grade(page) // 전부 비움 → 3개 다 오답·⭐
    await expect(summary(page)).toContainText('⭐ 완료')

    let release = () => {}
    const held = new Promise<void>((resolve) => (release = resolve))
    await page.route(STAR_API, async (route) => {
      await held
      await route.continue()
    }, { times: 1 })
    const first = page.locator('.otest-row').first()
    await first.getByRole('button', { name: '맞음으로' }).click()

    const retry = page.getByRole('button', { name: /다시 풀기/ })
    await expect(retry).toBeDisabled({ timeout: 2_000 })
    release()
    await expect(first.getByRole('button', { name: '다시 오답으로' })).toBeVisible()
    await expect(retry).toBeEnabled()
    await expect(retry).toHaveText('틀린 2개만 다시 풀기')
  })

  // 고치기 전: 501개를 한 번에 보내 400으로 거절됐고, 다시 시도해도 같았다
  test('오답이 500개를 넘어도 전부 ⭐ 된다 (서버는 한 번에 500장까지 받음)', async ({ page, request }) => {
    test.setTimeout(120_000)
    const lines = Array.from({ length: 501 }, (_, i) => `word${i + 1}|뜻${i + 1}`)
    const deckId = await makeDeck(request, '501', lines)
    await open(page, deckId)
    await expect(page.locator('.otest-row')).toHaveCount(501)
    await grade(page) // 전부 비움 → 501개 오답
    await expect(summary(page)).toContainText('⭐ 완료', { timeout: 30_000 })
    const starred = (await listCards(request, deckId)).filter((c) => c.starred).length
    expect(starred).toBe(501)
  })

  // 고치기 전: 다시 시도 응답이 "바뀐 카드 없음"이라 이 시험이 켠 ⭐인지 몰라서 안 껐다
  test('별표 응답이 끊겨도(서버엔 저장됨) 다시 시도 뒤 맞음으로가 그 ⭐를 끈다', async ({ page, request, errors }) => {
    const deckId = await makeDeck(request, '응답유실', ['感謝|かんしゃ|감사', '会議|かいぎ|회의'])
    await open(page, deckId)
    // 첫 별표 요청: 서버까지는 보내고(저장됨), 브라우저에는 네트워크 오류로 돌려준다
    await page.route(STAR_API, async (route) => {
      await route.fetch()
      await route.abort('failed')
    }, { times: 1 })
    await grade(page)
    await expect(page.getByRole('button', { name: '별표 다시 시도' })).toBeVisible()
    expect(await isStarred(request, deckId, '会議')).toBe(true) // 서버에는 저장됐다
    errors.length = 0 // 일부러 끊은 요청의 콘솔 오류는 빼고 본다

    await page.getByRole('button', { name: '별표 다시 시도' }).click()
    await expect(summary(page)).toContainText('⭐ 완료')
    await row(page, '会議').getByRole('button', { name: '맞음으로' }).click()
    await expect(row(page, '会議').getByRole('button', { name: '다시 오답으로' })).toBeVisible()
    expect(await isStarred(request, deckId, '会議')).toBe(false)
  })
})
