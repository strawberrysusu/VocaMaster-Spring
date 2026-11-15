import { test, expect, makeDeck } from './support'

// 학습 화면 (11/15 점검에서 나온 것)

test('답이 1000개를 넘으면 1000개씩 나눠 보낸다 — 예전엔 400으로 막혀 답을 전부 버려야 했다', async ({ page, request }) => {
  const deckId = await makeDeck(request, '나눠보내기', ['apple|사과'])
  const base = '3f2b8c1e-9a4d-4e7b-b6c2-0d5e8f1a2001'
  // 답 1001개가 든 미제출 초안을 심는다 (정말 1001장을 답하면 너무 느리다) — 카드 번호는 가짜지만 요청은 아래에서 가로챈다
  await page.addInitScript(
    ([key, submissionId]) => {
      const answers: Record<string, boolean> = {}
      for (let i = 1; i <= 1001; i++) answers[String(900000000 + i)] = true
      sessionStorage.setItem(key, JSON.stringify({ submissionId, answers }))
    },
    [`vm.study.draft.${deckId}`, base],
  )
  const sent: { submissionId: string; answers: unknown[] }[] = []
  await page.route('**/reviews/answers/batch', async (route) => {
    const body = route.request().postDataJSON() as { submissionId: string; answers: unknown[] }
    sent.push(body)
    const n = body.answers.length
    await route.fulfill({ json: { submissionId: body.submissionId, total: n, known: n, unknown: 0, alreadySubmitted: false, results: [], skippedIds: [] } })
  })

  await page.goto(`/app/study?deckId=${deckId}`)
  await page.locator('.study-card').waitFor()
  await page.keyboard.press('ArrowRight') // 하나뿐인 카드를 지나 '제출할까요?'
  await page.getByRole('button', { name: '학습 완료' }).click()

  await expect(page.getByText('이번 답변 1001장')).toBeVisible() // 두 묶음의 합
  expect(sent.map((s) => s.answers.length)).toEqual([1000, 1])
  expect(sent[0].submissionId).toBe(base) // 첫 묶음은 원래 ID
  expect(sent[1].submissionId).toBe(`${base.slice(0, 32)}~001`) // 둘째 묶음은 앞 32자 + ~001
})

test('카드 학습을 마치면 덱으로 돌아갈 수 있고, 홈의 이어하기도 카드 학습으로 열린다', async ({ page, request }) => {
  const deckId = await makeDeck(request, '카드학습이어', ['apple|사과'])
  await page.goto(`/app/study?deckId=${deckId}&mode=browse`)
  await page.locator('.study-card').waitFor()
  await page.keyboard.press('ArrowRight')
  await page.getByRole('button', { name: '학습 완료' }).click()

  await expect(page.getByRole('heading', { name: '카드 보기 완료' })).toBeVisible()
  await expect(page.getByText('1장을 다 봤어요')).toBeVisible()
  await expect(page.getByRole('link', { name: '덱으로' })).toHaveAttribute('href', `/app/decks/${deckId}`)

  await page.goto('/app/')
  await expect(page.locator('.recent-row').first().getByRole('link', { name: '이어하기' })).toHaveAttribute(
    'href',
    `/app/study?deckId=${deckId}&mode=browse`,
  )
})
