import { test, expect, makeDeck } from './support'

// 학습 랭킹 (11/14 사용자 요청, ADR-059) — 답한 카드 1장 = 1점, 왼쪽 메뉴 '랭킹'·홈 '오늘의 랭킹', 설정의 닉네임 가리기

test('학습 랭킹 — 답하면 오늘 탭에 내 줄이 생기고, 홈 카드에도 내 순위가 보인다', async ({ page, request }) => {
  const deckId = await makeDeck(request, '랭킹', ['会議|かいぎ|회의'])
  await page.goto(`/app/study?deckId=${deckId}`)
  await page.locator('.study-card').waitFor() // 카드가 뜨기 전의 키는 학습 화면이 무시한다
  await page.keyboard.press('KeyD') // 알아요 — 하나뿐인 카드라 바로 '제출할까요?'
  await page.getByRole('button', { name: '학습 완료' }).click()
  await expect(page.getByRole('heading', { name: '복습 완료 🎉' })).toBeVisible()

  await page.goto('/app/ranking')
  const mine = page.locator('.rank-row.me')
  await expect(mine).toBeVisible()
  await expect(mine).toContainText('나')
  await expect(mine.locator('.rank-points')).toContainText('점')

  await page.goto('/app/')
  await expect(page.locator('.rank-card .num')).toContainText('위')
})

test('설정에서 닉네임 가리기를 켜면 랭킹의 내 이름이 가려지고, 끄면 돌아온다', async ({ page }) => {
  await page.goto('/app/settings')
  const toggle = page.getByLabel('랭킹에서 닉네임 가리기')
  await expect(toggle).toBeEnabled()
  await toggle.check()
  await expect(toggle).toBeChecked()

  await page.goto('/app/ranking')
  await expect(page.locator('.rank-row.me .rank-name')).toContainText('***')

  await page.goto('/app/settings')
  await page.getByLabel('랭킹에서 닉네임 가리기').uncheck() // 다른 테스트에 남기지 않는다
  await expect(page.getByLabel('랭킹에서 닉네임 가리기')).not.toBeChecked()
})
