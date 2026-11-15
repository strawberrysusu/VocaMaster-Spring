import type { APIRequestContext, Page } from '@playwright/test'
import { test, expect, makeDeck } from './support'

// 학습 랭킹 (ADR-059) — 답한 카드 1장 = 1점, 왼쪽 메뉴 '랭킹'·홈 '오늘의 랭킹'.
// 닉네임은 기본으로 가려지고(qjw***), 공개는 설정에서 직접 체크해야 한다 (11/15 사용자 결정)

/** 카드 한 장을 '알아요'로 제출 — 오늘 랭킹에 내 줄이 생긴다 */
async function studyOneCard(page: Page, request: APIRequestContext, label: string) {
  const deckId = await makeDeck(request, label, ['会議|かいぎ|회의'])
  await page.goto(`/app/study?deckId=${deckId}`)
  await page.locator('.study-card').waitFor() // 카드가 뜨기 전의 키는 학습 화면이 무시한다
  await page.keyboard.press('KeyD') // 알아요 — 하나뿐인 카드라 바로 '제출할까요?'
  await page.getByRole('button', { name: '학습 완료' }).click()
  await expect(page.getByRole('heading', { name: '복습 완료 🎉' })).toBeVisible()
}

/** 설정의 '랭킹에 닉네임 공개'를 켜거나 끄고, 서버 저장이 끝날 때까지 기다린다 */
async function setNicknamePublic(page: Page, on: boolean) {
  await page.goto('/app/settings')
  const toggle = page.getByLabel('랭킹에 닉네임 공개')
  await expect(toggle).toBeEnabled()
  const saved = page.waitForResponse((r) => r.url().includes('/users/me/ranking') && r.ok())
  await toggle.setChecked(on)
  await saved
}

test('학습 랭킹 — 답하면 오늘 탭에 내 줄이 생기고, 홈 카드에도 내 순위가 보인다', async ({ page, request }) => {
  await studyOneCard(page, request, '랭킹')

  await page.goto('/app/ranking')
  const mine = page.locator('.rank-row.me')
  await expect(mine).toBeVisible()
  await expect(mine).toContainText('나')
  await expect(mine.locator('.rank-points')).toContainText('점')

  await page.goto('/app/')
  await expect(page.locator('.rank-card .num')).toContainText('위')
})

test('닉네임은 기본으로 가려지고, 설정에서 공개를 체크해야 그대로 보인다', async ({ page, request }) => {
  await studyOneCard(page, request, '랭킹공개')
  const myName = page.locator('.rank-row.me .rank-name')

  await page.goto('/app/settings')
  await expect(page.getByLabel('랭킹에 닉네임 공개')).toBeEnabled()
  await expect(page.getByLabel('랭킹에 닉네임 공개')).not.toBeChecked() // 새 계정의 기본값 = 비공개
  await page.goto('/app/ranking')
  await expect(myName).toContainText('***')

  await setNicknamePublic(page, true)
  await page.goto('/app/ranking')
  await expect(myName).toBeVisible()
  await expect(myName).not.toContainText('***')

  await setNicknamePublic(page, false) // 기본값으로 되돌려 다른 테스트에 남기지 않는다
  await page.goto('/app/ranking')
  await expect(myName).toContainText('***')
})

test('설정에서 닉네임을 바꾸면 위쪽 프로필 메뉴에 바로 보인다', async ({ page }) => {
  await page.goto('/app/settings')
  const input = page.getByLabel('닉네임', { exact: true })
  await expect(input).toBeEnabled()
  const before = await input.inputValue()
  const next = `e2e-${Date.now() % 100000}`

  await input.fill(next)
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.getByText('닉네임을 바꿨어요')).toBeVisible()
  await page.getByRole('button', { name: '내 프로필' }).click()
  await expect(page.getByRole('menu')).toContainText(next)
  await page.keyboard.press('Escape')

  await input.fill(before) // 되돌린다
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.getByText('닉네임을 바꿨어요')).toBeVisible()
})
