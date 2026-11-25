import type { APIRequestContext, Page } from '@playwright/test'
import { test, expect, makeDeck, listCards, isStarred, toggleStar, setStars, overflowX, LONG, makeEmptyDeck, addCard, deleteCard } from './support'

// 온라인 시험지(ADR-053 → ADR-063): 채점하면 틀리거나 비운 단어는 자동 ⭐, 맞힌 단어와 '맞음으로'는 ⭐를 끈다(예전부터 켜져 있던 것도)

const open = (page: Page, deckId: number) => page.goto(`/app/test/${deckId}?scope=all&count=all&shuffle=0`)
const row = (page: Page, word: string) =>
  page.locator('.otest-row').filter({ has: page.locator('.otest-word', { hasText: word }) })
const summary = (page: Page) => page.locator('.otest-summary')
const grade = (page: Page) => page.getByRole('button', { name: /채점하기/ }).click()
const STAR_API = '**/decks/*/cards/star'

// ADR-063 (11/18 사용자 결정 "시험으로 맞춰야만 정답으로 인정"): 맞힌 단어와 '맞음으로'는 시험 전부터 켜져 있던 ⭐도 끈다.
// 그 전(ADR-053)에는 이 시험이 켠 ⭐만 껐다 — 원래 ⭐였던 ジャンケン은 맞음으로를 눌러도 남았고, 맞힌 단어의 ⭐는 건드리지 않았다
test('채점: 틀리거나 비운 단어는 ⭐, 맞힌 단어와 맞음으로는 예전부터 켜져 있던 ⭐도 끈다', async ({ page, request }) => {
  const deckId = await makeDeck(request, '온라인', ['感謝|かんしゃ|감사', '会議|かいぎ|회의', 'ジャンケン|가위바위보', '運輸|うんゆ|운수'])
  const cards = await listCards(request, deckId)
  await toggleStar(request, cards.find((c) => c.front === 'ジャンケン')!.id) // 시험 전부터 ⭐, 이번에 틀린다
  await toggleStar(request, cards.find((c) => c.front === '運輸')!.id) // 시험 전부터 ⭐, 이번에 맞힌다

  await open(page, deckId)
  await row(page, '感謝').getByLabel(/읽기$/).fill('カンシャ') // 가타카나로 써도 정답
  await row(page, '感謝').getByLabel(/뜻$/).fill('감사')
  await row(page, '運輸').getByLabel(/읽기$/).fill('うんゆ')
  await row(page, '運輸').getByLabel(/뜻$/).fill('운수')
  await row(page, 'ジャンケン').getByLabel(/뜻$/).fill('보') // 오답
  // 会議는 비워 둔다 = 모름
  await grade(page)

  await expect(summary(page)).toContainText('점수 2/4')
  await expect(summary(page)).toContainText('틀린 2개 ⭐ 완료 (새로 켠 별표 1개)') // 会議만 새로 켰다
  await expect(summary(page)).toContainText('맞힌 단어의 ⭐ 1개 해제') // 運輸
  expect(await isStarred(request, deckId, '会議')).toBe(true)
  expect(await isStarred(request, deckId, 'ジャンケン')).toBe(true)
  expect(await isStarred(request, deckId, '感謝')).toBe(false)
  expect(await isStarred(request, deckId, '運輸')).toBe(false) // 맞히면 예전 ⭐도 꺼진다

  await row(page, '会議').getByRole('button', { name: '맞음으로' }).click()
  await expect(row(page, '会議').getByRole('button', { name: '다시 오답으로' })).toBeVisible()
  await row(page, 'ジャンケン').getByRole('button', { name: '맞음으로' }).click()
  await expect(row(page, 'ジャンケン').getByRole('button', { name: '다시 오답으로' })).toBeVisible()
  expect(await isStarred(request, deckId, '会議')).toBe(false) // 이 시험이 켠 ⭐ → 끔
  expect(await isStarred(request, deckId, 'ジャンケン')).toBe(false) // 원래 ⭐였어도 끔 (ADR-063)
  await expect(summary(page)).toContainText('점수 4/4')
  await expect(summary(page)).toContainText('맞힌 단어의 ⭐ 3개 해제')
  await expect(summary(page)).not.toContainText('틀린') // 틀린 단어가 없으면 '틀린 N개' 문구도 없다

  // 다시 오답으로 → 다시 켠다. 원래 ⭐였던 카드를 되돌린 것은 '새로 켠 별표'로 세지 않는다
  await row(page, '会議').getByRole('button', { name: '다시 오답으로' }).click()
  await expect(row(page, '会議').getByRole('button', { name: '맞음으로' })).toBeVisible()
  await row(page, 'ジャンケン').getByRole('button', { name: '다시 오답으로' }).click()
  await expect(row(page, 'ジャンケン').getByRole('button', { name: '맞음으로' })).toBeVisible()
  expect(await isStarred(request, deckId, '会議')).toBe(true)
  expect(await isStarred(request, deckId, 'ジャンケン')).toBe(true)
  await expect(summary(page)).toContainText('틀린 2개 ⭐ 완료 (새로 켠 별표 1개)')
  await expect(summary(page)).toContainText('맞힌 단어의 ⭐ 1개 해제')
})

// ADR-063: 다시 풀기에서 맞히면 앞 풀이가 켠 ⭐가 꺼지고, 전부 맞힌 풀이에도 별표 정리 결과가 보인다
test('다시 풀기에서 맞힌 단어는 앞 풀이가 켠 ⭐가 꺼진다', async ({ page, request }) => {
  const deckId = await makeDeck(request, '다시풀기해제', ['apple|사과', 'banana|바나나'])
  await open(page, deckId)
  await row(page, 'apple').getByLabel(/뜻$/).fill('사과')
  await grade(page) // banana는 비움 → ⭐
  await expect(summary(page)).toContainText('틀린 1개 ⭐ 완료 (새로 켠 별표 1개)')
  await expect(summary(page)).not.toContainText('해제') // apple은 원래 ⭐가 없었다 — 끈 별표 0개면 문구도 없다
  expect(await isStarred(request, deckId, 'banana')).toBe(true)

  await page.getByRole('button', { name: /다시 풀기/ }).click()
  await expect(page.locator('.otest-row')).toHaveCount(1)
  await row(page, 'banana').getByLabel(/뜻$/).fill('바나나')
  await grade(page)
  await expect(summary(page)).toContainText('점수 1/1')
  await expect(summary(page)).toContainText('맞힌 단어의 ⭐ 1개 해제')
  await expect(summary(page)).not.toContainText('틀린')
  expect(await isStarred(request, deckId, 'banana')).toBe(false)
})

// ADR-063: 전부 맞힌 풀이에서도 별표 끄기 요청이 나간다 — 실패하면 '별표 다시 시도'가 보여야 한다
// (예전에는 틀린 단어가 없으면 별표 줄 자체를 그리지 않았다)
test('전부 맞혔는데 별표 끄기가 실패하면 별표 다시 시도로 끈다', async ({ page, request, errors }) => {
  const deckId = await makeDeck(request, '해제실패', ['apple|사과'])
  const apple = (await listCards(request, deckId)).find((c) => c.front === 'apple')!
  await toggleStar(request, apple.id) // 시험 전부터 ⭐
  await open(page, deckId)
  await row(page, 'apple').getByLabel(/뜻$/).fill('사과')
  await page.route(STAR_API, (route) => route.abort('failed'), { times: 1 }) // 서버까지 안 감 = 아직 안 꺼짐
  await grade(page)
  await expect(summary(page)).toContainText('점수 1/1')
  await expect(page.getByRole('button', { name: '별표 다시 시도' })).toBeVisible()
  await expect(page.getByRole('alert')).toContainText('맞힌 단어의 별표를 끄지 못했어요')
  expect(await isStarred(request, deckId, 'apple')).toBe(true)
  errors.length = 0 // 일부러 끊은 요청의 콘솔 오류는 빼고 본다

  await page.getByRole('button', { name: '별표 다시 시도' }).click()
  await expect(summary(page)).toContainText('맞힌 단어의 ⭐ 1개 해제')
  await expect(page.getByRole('alert')).toHaveCount(0)
  expect(await isStarred(request, deckId, 'apple')).toBe(false)
})

// ADR-062 (11/18 사용자 결정): 띄어쓰기가 달라도, 뜻을 순서 바꿔 쉼표로 적어도 정답. 카드에 없는 뜻이 섞이면 오답
test('채점: 띄어쓰기·뜻 순서가 달라도 정답, 카드에 없는 뜻이 섞이면 오답', async ({ page, request }) => {
  const deckId = await makeDeck(request, '채점규칙', [
    'unavoidable|a. 불가피한, 피할 수 없는',
    'tenacity|n. 고집, 끈기',
    'odd|a. 이상한, 기묘한; 홀수의',
  ])

  await open(page, deckId)
  await row(page, 'unavoidable').getByLabel(/뜻$/).fill('피할수없는') // 띄어쓰기 없이
  await row(page, 'tenacity').getByLabel(/뜻$/).fill('끈기,고집') // 순서를 바꿔 둘 다
  await row(page, 'odd').getByLabel(/뜻$/).fill('더하다, 이상한') // '더하다'는 카드에 없는 뜻
  await grade(page)

  await expect(summary(page)).toContainText('점수 2/3')
  await expect(summary(page)).toContainText('⭐ 완료') // 별표 저장이 끝난 뒤에 서버 값을 본다
  await expect(row(page, 'unavoidable').getByRole('button', { name: '맞음으로' })).toHaveCount(0)
  await expect(row(page, 'tenacity').getByRole('button', { name: '맞음으로' })).toHaveCount(0)
  await expect(row(page, 'odd').getByRole('button', { name: '맞음으로' })).toBeVisible()
  expect(await isStarred(request, deckId, 'odd')).toBe(true)
  expect(await isStarred(request, deckId, 'tenacity')).toBe(false)
})

// ADR-066 (11/25): 채점 뒤 '틀린 것만 보기' — 정답과 겹치는 데가 있는 답(확인할 것)은 위, 없는 답은 아래, 맞힌 답은 숨긴다.
// 대괄호 [ ]가 붙은 뜻은 '앞 낱말 대신 쓸 수 있는 말'로 채점한다 (전에는 none — "아무것도 않다"가 오답이었다)
test('틀린 것만 보기 — 비슷하게 쓴 답은 위, 틀린 답은 아래 + 대괄호 뜻 채점', async ({ page, request }) => {
  const deckId = await makeDeck(request, '틀린것만', [
    'apple|n. 사과',
    'pull|v. 잡아당기다, 끌다',
    'wealth|n. 부, 재산',
    'none|pron. 아무도[아무것도] ~않다',
    'photographer|n. 사진사',
  ])
  await open(page, deckId)
  await row(page, 'apple').getByLabel(/뜻$/).fill('사과') // 정답
  await row(page, 'pull').getByLabel(/뜻$/).fill('당기다') // 비슷 — '잡아당기다'를 품음
  await row(page, 'wealth').getByLabel(/뜻$/).fill('건강한') // 틀림 — health와 헷갈림
  await row(page, 'none').getByLabel(/뜻$/).fill('아무것도 않다') // 정답 — [ ] 안의 말로 바꿔 적음
  await row(page, 'photographer').getByLabel(/뜻$/).fill('사진가') // 비슷 — 앞 두 글자
  await grade(page)
  await expect(summary(page)).toContainText('점수 2/5')
  await expect(summary(page)).toContainText('⭐ 완료')

  await expect(page.locator('.otest-row')).toHaveCount(5) // 처음엔 다 보인다
  await page.getByLabel('틀린 것만 보기 (3)').check()
  await expect(page.locator('.otest-row')).toHaveCount(3)
  await expect(page.locator('.otest-group')).toHaveText(['비슷하게 썼어요 — 맞았는지 확인해 보세요 (2)', '틀렸어요 (1)'])
  const words = await page.locator('.otest-row .otest-word').allTextContents()
  expect(words.slice(0, 2).sort()).toEqual(['photographer', 'pull']) // 위 묶음
  expect(words[2]).toBe('wealth') // 아래 묶음

  // 맞음으로를 눌러도 그 자리에 남는다 — 바로 '다시 오답으로'로 되돌릴 수 있게
  await row(page, 'pull').getByRole('button', { name: '맞음으로' }).click()
  await expect(row(page, 'pull').getByRole('button', { name: '다시 오답으로' })).toBeVisible()
  await expect(page.locator('.otest-row')).toHaveCount(3)
  await expect(summary(page)).toContainText('점수 3/5')

  // 켜 둔 것은 이 브라우저에 기억 — 다음 시험의 결과도 처음부터 묶어서 보여 준다
  await page.reload()
  await row(page, 'apple').getByLabel(/뜻$/).fill('사과')
  await grade(page)
  await expect(summary(page)).toContainText('⭐ 완료')
  await expect(page.getByLabel('틀린 것만 보기 (4)')).toBeChecked()
  await expect(page.locator('.otest-row')).toHaveCount(4)
})

test('폰 화면: 채점 뒤 정답·설명 줄이 붙어도 가로로 넘치지 않는다', async ({ page, request }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  const deckId = await makeEmptyDeck(request, '온라인폰')
  await addCard(request, deckId, { front: LONG.word, reading: LONG.reading, back: LONG.back, memo: LONG.memo })
  await addCard(request, deckId, { front: 'ざら', back: '아주 흔함' })
  await open(page, deckId)
  await expect(page.locator('.otest-row')).toHaveCount(2)
  // 페이지 넘침 + 시험지 안에서 잘려 안 보이는 넘침까지 (시험지 자체의 scrollWidth — 최종 검토 11/14)
  const sheetOverflow = () => page.locator('.otest-sheet').evaluate((el) => el.scrollWidth - el.clientWidth)
  expect(await overflowX(page)).toBe(0)
  expect(await sheetOverflow()).toBe(0)
  await grade(page)
  await expect(summary(page)).toContainText('⭐ 완료')
  await expect(page.locator('.otest-memo')).toBeVisible()
  expect(await overflowX(page)).toBe(0)
  expect(await sheetOverflow()).toBe(0)
})

// 11/14 검토 워크플로가 찾음: 다시 풀기 중 새로고침하면 '이 시험이 켠 ⭐' 기록을 잃어 맞음으로가 ⭐를 못 껐다
test('다시 풀기 중에 새로고침해도 이 시험이 켠 ⭐는 맞음으로가 끈다', async ({ page, request }) => {
  const deckId = await makeDeck(request, '이어풀기', ['感謝|かんしゃ|감사', '会議|かいぎ|회의'])
  await open(page, deckId)
  await grade(page) // 둘 다 비움 → 둘 다 ⭐
  await expect(summary(page)).toContainText('⭐ 완료')
  await page.getByRole('button', { name: /다시 풀기/ }).click()
  await expect(page.locator('.otest-row')).toHaveCount(2)

  await page.reload() // 답안 임시 저장에서 다시 풀기 문항이 돌아온다
  await expect(page.locator('.otest-row')).toHaveCount(2)
  await grade(page)
  await expect(summary(page)).toContainText('⭐ 완료')
  await row(page, '会議').getByRole('button', { name: '맞음으로' }).click()
  await expect(row(page, '会議').getByRole('button', { name: '다시 오답으로' })).toBeVisible()
  expect(await isStarred(request, deckId, '会議')).toBe(false)
})

// 11/14 검토: 시험 도중 그 덱의 카드가 지워지면(다른 탭·기기) 별표 일괄 요청이 400으로 통째 거절돼,
// 나머지 틀린 단어까지 ⭐가 안 켜지고 '별표 다시 시도'도 계속 실패했다 → 서버가 지워진 카드만 건너뛴다
test('시험 도중 카드가 지워져도 나머지 틀린 단어는 ⭐ 되고, 지워진 카드는 건너뛴다', async ({ page, request }) => {
  const deckId = await makeDeck(request, '지워진카드', ['感謝|かんしゃ|감사', '会議|かいぎ|회의'])
  await open(page, deckId)
  await expect(page.locator('.otest-row')).toHaveCount(2)
  const gone = (await listCards(request, deckId)).find((c) => c.front === '感謝')!
  await deleteCard(request, gone.id) // 시험지는 열린 채로, 다른 탭에서 지운 것처럼

  await grade(page) // 둘 다 비움 → 둘 다 오답
  await expect(summary(page)).toContainText('지워진 카드 1개는 건너뜀')
  await expect(summary(page)).toContainText('새로 켠 별표 1개')
  expect(await isStarred(request, deckId, '会議')).toBe(true)

  // 지워진 카드 줄에서 맞음으로 → 다시 오답으로를 눌러도 '새로 켠 별표'로 세지 않는다 (검증 에이전트가 찾은 것)
  await row(page, '感謝').getByRole('button', { name: '맞음으로' }).click()
  await expect(row(page, '感謝').getByRole('button', { name: '다시 오답으로' })).toBeVisible()
  await row(page, '感謝').getByRole('button', { name: '다시 오답으로' }).click()
  await expect(row(page, '感謝').getByRole('button', { name: '맞음으로' })).toBeVisible()
  await expect(summary(page)).toContainText('새로 켠 별표 1개')
})

// 최종 검토(11/14): 자동 ⭐가 실패한 채로 '다시 풀기'를 누를 수 있어, 실패한 ⭐는 끝내 다시 시도되지 않고
// '새로 켠 별표' 수만 부풀었다 → 실패하면 '별표 다시 시도'를 먼저, 수는 서버가 확인한 것만
test('자동 ⭐가 실패한 채로는 다시 풀기가 막히고, 별표 다시 시도가 성공하면 풀 수 있다', async ({ page, request, errors }) => {
  const deckId = await makeDeck(request, '별표실패', ['感謝|かんしゃ|감사', '会議|かいぎ|회의'])
  await open(page, deckId)
  await page.route(STAR_API, (route) => route.abort('failed'), { times: 1 }) // 서버까지 안 감 = 아무것도 저장 안 됨
  await grade(page)
  await expect(page.getByRole('button', { name: '별표 다시 시도' })).toBeVisible()
  errors.length = 0 // 일부러 끊은 요청의 콘솔 오류는 빼고 본다
  const retest = page.getByRole('button', { name: /다시 풀기/ })
  await expect(retest).toBeDisabled()
  await expect(retest).toHaveAttribute('title', '별표를 먼저 다시 시도해 주세요')

  await page.getByRole('button', { name: '별표 다시 시도' }).click()
  await expect(summary(page)).toContainText('새로 켠 별표 2개')
  await expect(retest).toBeEnabled()
  expect(await isStarred(request, deckId, '感謝')).toBe(true)
  expect(await isStarred(request, deckId, '会議')).toBe(true)
})

// 최종 검토(11/14): '맞음으로' 요청이 끝나기 전에 '별표 다시 시도'를 누르면 방금 맞음으로 한 단어에 ⭐가 다시 켜졌다
test('맞음으로 저장 중에는 별표 다시 시도를 누를 수 없다', async ({ page, request, errors }) => {
  const deckId = await makeDeck(request, '다시시도경합', ['感謝|かんしゃ|감사', '会議|かいぎ|회의'])
  await open(page, deckId)
  await page.route(STAR_API, (route) => route.abort('failed'), { times: 1 })
  await grade(page)
  await expect(page.getByRole('button', { name: '별표 다시 시도' })).toBeVisible()
  errors.length = 0

  let release = () => {}
  const held = new Promise<void>((resolve) => (release = resolve))
  await page.route(STAR_API, async (route) => {
    await held
    await route.continue()
  }, { times: 1 })
  await row(page, '会議').getByRole('button', { name: '맞음으로' }).click()
  await expect(page.getByRole('button', { name: '별표 다시 시도' })).toBeDisabled()
  release()
  await expect(row(page, '会議').getByRole('button', { name: '다시 오답으로' })).toBeVisible()
  await expect(page.getByRole('button', { name: '별표 다시 시도' })).toBeEnabled()
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
    await expect(page.getByRole('alert')).toHaveCount(0) // 성공했으면 앞서 뜬 오류 문구도 사라진다
    await row(page, '会議').getByRole('button', { name: '맞음으로' }).click()
    await expect(row(page, '会議').getByRole('button', { name: '다시 오답으로' })).toBeVisible()
    expect(await isStarred(request, deckId, '会議')).toBe(false)
  })
})

// ── 회귀: Codex 9차 검산(11/18)이 찾은 것 — 네 개 모두 고치기 전 코드에서 실패하는 것을 먼저 확인했다
test.describe('회귀 — 별표 저장 중 화면 이동·해제 개수 (Codex 9차 검산 11/18)', () => {
  // 시험 전부터 ⭐인 apple은 맞히고(꺼져야 한다), banana는 비운다(켜져야 한다). 별표 요청은 release()까지 붙잡아 둔다
  async function gradeWithStarsHeld(page: Page, request: APIRequestContext, label: string) {
    const deckId = await makeDeck(request, label, ['apple|사과', 'banana|바나나'])
    const apple = (await listCards(request, deckId)).find((c) => c.front === 'apple')!
    await toggleStar(request, apple.id)
    await open(page, deckId)
    await row(page, 'apple').getByLabel(/뜻$/).fill('사과')

    let release = () => {}
    const held = new Promise<void>((resolve) => (release = resolve))
    await page.route(STAR_API, async (route) => {
      await held
      await route.continue()
    })
    await grade(page)
    await expect(summary(page)).toContainText('⭐ 정리하는 중')
    return { deckId, release }
  }

  // 고치기 전: 저장 중에도 눌려서 복습 화면이 저장 전 별표로 열렸다 — 방금 맞힌 apple이 나오고 틀린 banana는 빠졌다.
  // 복습 화면은 들어올 때 목록을 한 번만 읽으니, 저장이 끝나도 새로 고치기 전에는 그대로였다
  test('별표 저장이 끝나기 전에는 "별표만 복습"이 막혀 있고, 끝난 뒤의 복습 목록은 채점 결과와 같다', async ({ page, request }) => {
    const { deckId, release } = await gradeWithStarsHeld(page, request, '저장중복습')
    const review = page.locator('.answer-buttons').getByText('⭐ 별표만 복습 하러 가기')
    await expect(review).toBeDisabled({ timeout: 2_000 })
    await expect(review).toHaveAttribute('title', '별표 저장이 끝나면 누를 수 있어요')

    release()
    await expect(summary(page)).toContainText('맞힌 단어의 ⭐ 1개 해제')
    await expect(review).toBeEnabled()
    await review.click()
    await expect(page).toHaveURL(new RegExp(`/app/study\\?deckId=${deckId}&starredOnly=1$`))
    await expect(page.locator('.study-word')).toHaveText('banana') // 틀린 단어만 나온다
    await expect(page.getByText(/^1 \/ 1 · /)).toBeVisible()
  })

  // 고치기 전: 저장 중에 덱 화면으로 나가면 덱 화면이 저장 전 별표를 읽어, 새로 고칠 때까지 옛 별표가 보였다.
  // 이제 카드 목록 읽기는 그 덱의 별표 쓰기가 끝난 뒤에 출발한다 (api/cards.ts — trackStarWrite)
  test('별표 저장 중에 덱 화면으로 나가도 덱 화면은 저장이 끝난 뒤의 별표를 보여 준다', async ({ page, request }) => {
    const { deckId, release } = await gradeWithStarsHeld(page, request, '저장중덱이동')
    await page.getByRole('link', { name: '← 덱으로' }).click()
    await expect(page).toHaveURL(new RegExp(`/app/decks/${deckId}$`))
    await expect(page.getByText('카드 불러오는 중...')).toBeVisible() // 별표 저장이 끝날 때까지 목록을 읽지 않는다

    release()
    await expect(page.getByText('카드 2장 · 별표 1장')).toBeVisible()
    const starOf = (word: string) => page.locator('.owned-card-row', { hasText: word }).locator('.star-btn')
    await expect(starOf('banana')).toHaveAttribute('aria-pressed', 'true')
    await expect(starOf('apple')).toHaveAttribute('aria-pressed', 'false')
    expect(await isStarred(request, deckId, 'banana')).toBe(true)
    expect(await isStarred(request, deckId, 'apple')).toBe(false)
  })

  // 고치기 전: 끄기 요청이 서버에는 반영됐는데 응답만 끊기면, 다시 시도의 응답은 "바뀐 카드 없음"이라 해제 문구가 아예 안 나왔다
  test('별표 끄기 응답이 끊겨도(서버엔 저장됨) 다시 시도 뒤 해제 개수가 맞다', async ({ page, request, errors }) => {
    const deckId = await makeDeck(request, '해제응답유실', ['apple|사과'])
    const apple = (await listCards(request, deckId)).find((c) => c.front === 'apple')!
    await toggleStar(request, apple.id) // 시험 전부터 ⭐
    await open(page, deckId)
    await row(page, 'apple').getByLabel(/뜻$/).fill('사과')
    // 끄기 요청: 서버까지는 보내고(꺼짐), 브라우저에는 네트워크 오류로 돌려준다
    await page.route(STAR_API, async (route) => {
      await route.fetch()
      await route.abort('failed')
    }, { times: 1 })
    await grade(page)
    await expect(page.getByRole('button', { name: '별표 다시 시도' })).toBeVisible()
    expect(await isStarred(request, deckId, 'apple')).toBe(false) // 서버에서는 이미 꺼졌다
    errors.length = 0 // 일부러 끊은 요청의 콘솔 오류는 빼고 본다

    await page.getByRole('button', { name: '별표 다시 시도' }).click()
    await expect(summary(page)).toContainText('맞힌 단어의 ⭐ 1개 해제')
    await expect(page.getByRole('alert')).toHaveCount(0)
  })

  // 고치기 전: 500장씩 나눠 끄다가 마지막 묶음만 실패하면, 앞 묶음이 끈 500개를 세지 못해 다시 시도 뒤 '1개 해제'로 나왔다
  test('별표 501개를 끄다 마지막 묶음이 끊겨도 다시 시도 뒤 501개 해제로 나온다', async ({ page, request, errors }) => {
    test.setTimeout(120_000)
    const lines = Array.from({ length: 501 }, (_, i) => `word${i + 1}|뜻${i + 1}`)
    const deckId = await makeDeck(request, '해제501', lines)
    const ids = (await listCards(request, deckId)).map((c) => c.id)
    await setStars(request, deckId, ids, true) // 전부 시험 전부터 ⭐
    await open(page, deckId)
    await expect(page.locator('.otest-row')).toHaveCount(501)
    // 501칸을 한꺼번에 채운다 (word7 → 뜻7) — 한 칸씩 치면 너무 오래 걸린다
    await page.evaluate(() => {
      const setValue = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!
      for (const r of Array.from(document.querySelectorAll('.otest-row'))) {
        const word = r.querySelector('.otest-word')!.textContent ?? ''
        const input = r.querySelector<HTMLInputElement>('input.otest-input')!
        setValue.call(input, word.replace('word', '뜻'))
        input.dispatchEvent(new Event('input', { bubbles: true }))
      }
    })
    let calls = 0
    await page.route(STAR_API, async (route) => {
      if (++calls === 2) await route.abort('failed') // 두 번째 묶음(마지막 1장)만 끊는다 — 서버까지 안 감
      else await route.continue()
    })
    await grade(page)
    await expect(summary(page)).toContainText('점수 501/501')
    await expect(page.getByRole('button', { name: '별표 다시 시도' })).toBeVisible({ timeout: 30_000 })
    expect((await listCards(request, deckId)).filter((c) => c.starred).length).toBe(1) // 앞 묶음 500개는 이미 꺼졌다
    errors.length = 0

    await page.getByRole('button', { name: '별표 다시 시도' }).click()
    await expect(summary(page)).toContainText('맞힌 단어의 ⭐ 501개 해제', { timeout: 30_000 })
    expect((await listCards(request, deckId)).filter((c) => c.starred).length).toBe(0)
  })
})
