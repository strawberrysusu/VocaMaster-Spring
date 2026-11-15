import { test, expect, makeDeck, isStarred } from './support'

// 틀린 단어 자동 ⭐ (11/15 사용자 결정) — 온라인 시험지에 더해 퀴즈·타이핑·듣기에서도.
// 퀴즈·타이핑은 서버가 채점하면서 켜고, 듣기는 화면이 채점하니 화면이 일괄 별표 API로 켠다. 낱말카드 복습의 '몰라요'는 그대로

test('퀴즈: 틀린 단어는 바로 ⭐, 맞힌 단어는 그대로', async ({ page, request }) => {
  const deckId = await makeDeck(request, '퀴즈별표', ['apple|사과', 'banana|바나나'])
  const meaning = { apple: '사과', banana: '바나나' } as const
  await page.goto(`/app/quiz/${deckId}`)
  await page.getByRole('button', { name: '시작' }).click()

  // 첫 문제는 일부러 틀린다 — 문제로 나온 단어가 아닌 쪽의 뜻을 고른다
  const first = (await page.locator('.quiz-question').innerText()).includes('apple') ? 'apple' : 'banana'
  const other = first === 'apple' ? 'banana' : 'apple'
  await page.locator('.choice', { hasText: meaning[other] }).click()
  await expect(page.locator('.quiz-feedback .ng')).toBeVisible()
  await expect.poll(() => isStarred(request, deckId, first)).toBe(true) // 서버가 채점하면서 켠다

  // 둘째 문제는 맞힌다 → 별표 없음, 결과 화면에 안내
  await page.getByRole('button', { name: /다음/ }).click()
  await page.locator('.choice', { hasText: meaning[other] }).click()
  await expect(page.locator('.quiz-feedback .ok')).toBeVisible()
  await page.getByRole('button', { name: '결과 보기' }).click()
  await expect(page.getByText('틀린 단어는 자동으로 ⭐ 했어요')).toBeVisible()
  expect(await isStarred(request, deckId, other)).toBe(false)
})

test('타이핑: 틀린 단어는 바로 ⭐', async ({ page, request }) => {
  const deckId = await makeDeck(request, '타이핑별표', ['apple|사과'])
  await page.goto(`/app/typing/${deckId}`)
  await page.getByRole('button', { name: '시작' }).click()

  const input = page.getByLabel('답 입력')
  await input.fill('바나나')
  await input.press('Enter')
  await expect(page.getByText(/오답/)).toBeVisible()
  await expect.poll(() => isStarred(request, deckId, 'apple')).toBe(true)

  await page.getByRole('button', { name: '결과 보기' }).click()
  await expect(page.getByText('틀린 단어는 자동으로 ⭐ 했어요')).toBeVisible()
})

test('듣기: 채점하면 틀린 단어(빈칸 포함)에 ⭐', async ({ page, request }) => {
  const deckId = await makeDeck(request, '듣기별표', ['apple|사과', 'banana|바나나'])
  await page.goto(`/app/listening/${deckId}`)
  await page.getByRole('button', { name: /시험 시작/ }).click()
  await page.getByRole('button', { name: '채점하기' }).click() // 아무것도 안 쓰고 채점 = 둘 다 틀림

  await expect(page.getByText('틀린 2개 ⭐ 완료 (새로 켠 별표 2개)')).toBeVisible()
  expect(await isStarred(request, deckId, 'apple')).toBe(true)
  expect(await isStarred(request, deckId, 'banana')).toBe(true)
})

// 11/15 검토: 별표 요청이 실패한 채로 '다시 설정' → 새 시험을 시작하면 지난 회차의 오류 문구와
// '별표 다시 시도'가 새 시험에 그대로 남았다 (다 맞히면 끝까지 안 지워졌다)
test('듣기: 별표가 실패한 회차의 오류·다시 시도 버튼이 다음 시험에 남지 않는다', async ({ page, request, errors }) => {
  const deckId = await makeDeck(request, '듣기회차', ['apple|사과', 'banana|바나나'])
  await page.goto(`/app/listening/${deckId}`)
  await page.route('**/decks/*/cards/star', (route) => route.abort('failed'), { times: 1 }) // 서버까지 안 감
  await page.getByRole('button', { name: /시험 시작/ }).click()
  await page.getByRole('button', { name: '채점하기' }).click() // 둘 다 틀림 → 별표 요청 실패
  await expect(page.getByRole('button', { name: '별표 다시 시도' })).toBeVisible()
  await expect(page.getByRole('alert')).toBeVisible()
  errors.length = 0 // 일부러 끊은 요청의 콘솔 오류는 빼고 본다

  await page.getByRole('button', { name: '다시 설정' }).click()
  await page.getByRole('button', { name: /시험 시작/ }).click()
  await expect(page.getByRole('button', { name: '채점하기' })).toBeVisible()
  await expect(page.getByRole('alert')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '별표 다시 시도' })).toHaveCount(0)

  // 새 회차에서 다시 채점하면 이번엔 켜진다
  await page.getByRole('button', { name: '채점하기' }).click()
  await expect(page.getByText('틀린 2개 ⭐ 완료 (새로 켠 별표 2개)')).toBeVisible()
  expect(await isStarred(request, deckId, 'apple')).toBe(true)
})
