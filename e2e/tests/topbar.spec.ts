import { test, expect, makeEmptyDeck } from './support'

// 위쪽 바 (11/14 사용자 요청, Quizlet식) — 가운데 덱 검색, + 만들기 메뉴, 오른쪽 위 프로필 메뉴·언어.
// 왼쪽 사이드바는 메뉴·폴더만, 폰에서는 검색창 대신 돋보기 + 아래 탭바.

test('위쪽 바 검색 — 내 덱을 이름으로 찾아 들어간다', async ({ page, request }) => {
  const label = `찾기${Date.now()}`
  const deckId = await makeEmptyDeck(request, label)

  await page.goto('/app/')
  const search = page.getByRole('search').getByRole('textbox', { name: '덱 검색' })
  await search.fill(label)
  await search.press('Enter')

  await expect(page).toHaveURL(/\/app\/search\?q=/)
  await page.getByRole('link', { name: new RegExp(label) }).click()
  await expect(page).toHaveURL(new RegExp(`/app/decks/${deckId}$`))
})

test('+ 메뉴 — 새 덱은 내 덱 화면의 이름 칸으로, 새 폴더는 이름을 물어 만든다', async ({ page }) => {
  await page.goto('/app/')
  await page.getByRole('button', { name: '새로 만들기' }).click()
  await page.getByRole('menuitem', { name: '새 덱' }).click()
  await expect(page).toHaveURL(/\/app\/decks$/) // ?new=deck은 처리 뒤 주소에서 지운다
  await expect(page.getByLabel('새 덱 이름')).toBeFocused()

  const folder = `폴더${Date.now()}`
  page.once('dialog', (d) => d.accept(folder))
  await page.getByRole('button', { name: '새로 만들기' }).click()
  await page.getByRole('menuitem', { name: '새 폴더' }).click()
  await expect(page.locator('.side-folders')).toContainText(folder)
})

test('프로필 메뉴 — 오른쪽 위 아바타: 이메일·설정·로그아웃, Esc로 닫힌다', async ({ page }) => {
  await page.goto('/app/')
  await page.getByRole('button', { name: '내 프로필' }).click()
  const menu = page.getByRole('menu')
  await expect(menu).toContainText('@') // 이메일
  await expect(menu.getByRole('menuitem', { name: '설정' })).toBeVisible()
  await expect(menu.getByRole('menuitem', { name: '로그아웃' })).toBeVisible() // 누르지는 않는다 — 다른 테스트의 로그인이 풀린다
  await page.keyboard.press('Escape')
  await expect(page.getByRole('menu')).toHaveCount(0)
})

test('폰 — 위쪽 바는 돋보기로 검색 화면에 가고, 메뉴는 아래 탭바', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto('/app/')
  await expect(page.getByRole('search')).toHaveCount(0) // 위쪽 바의 검색창은 접힌다
  await expect(page.locator('.side-menu')).toBeVisible()

  await page.getByRole('link', { name: '덱 검색' }).click()
  await expect(page).toHaveURL(/\/app\/search$/)
  await expect(page.getByRole('textbox', { name: '덱 검색' })).toBeFocused() // 검색 화면의 큰 검색창
})
