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

  // 먼저 프로필 메뉴를 한 번 열어 지금 이름을 받아 두게 한다 — 메뉴는 받은 프로필을 기억해 두므로,
  // 이름을 바꾼 뒤 그 기억을 버리지 않으면(vm:profile-changed) 옛 이름이 그대로 보인다 (11/15 검토: 안 열고 바꾸면 이 테스트가 그 길을 안 지난다)
  await page.getByRole('button', { name: '내 프로필' }).click()
  await expect(page.getByRole('menu')).toContainText(before)
  await page.keyboard.press('Escape')
  await expect(page.getByRole('menu')).toHaveCount(0)

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

// Codex 5차 검산(11/16): 'A 저장 → 응답을 기다리는 동안 B 입력 → A 응답 도착'이면 칸이 A로 되돌아가 B가 사라졌다
test('닉네임 저장 응답을 기다리는 동안 더 친 글자는 응답이 와도 지워지지 않는다', async ({ page }) => {
  await page.goto('/app/settings')
  const input = page.getByLabel('닉네임', { exact: true })
  await expect(input).toBeEnabled()
  const before = await input.inputValue()
  const first = `e2e-a${Date.now() % 100000}`
  const second = `${first}-b`

  // 첫 저장(PATCH)의 응답만 붙잡아 둔다 — 풀어 준 뒤의 저장은 바로 지나간다
  let release: () => void = () => {}
  const held = new Promise<void>((resolve) => (release = resolve))
  await page.route('**/users/me', async (route) => {
    if (route.request().method() === 'PATCH') await held
    await route.continue()
  })
  const save = page.getByRole('button', { name: '저장', exact: true })

  await input.fill(first)
  await save.click()
  await expect(page.getByRole('button', { name: '저장 중...' })).toBeVisible()
  await input.fill(second) // 응답이 오기 전에 더 고친다
  const saved = page.waitForResponse((r) => r.request().method() === 'PATCH' && r.url().endsWith('/users/me'))
  release()
  await saved

  await expect(save).toBeEnabled() // 칸의 글자(second)가 저장된 값(first)과 다르니 다시 저장할 수 있다
  await expect(input).toHaveValue(second)
  await expect(page.getByText('닉네임을 바꿨어요')).toHaveCount(0) // 칸에 보이는 글자는 아직 저장 전

  await input.fill(before) // 되돌린다
  await save.click()
  await expect(page.getByText('닉네임을 바꿨어요')).toBeVisible()
})
