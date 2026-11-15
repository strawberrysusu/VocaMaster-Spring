import type { Page } from '@playwright/test'
import { test, expect, makeEmptyDeck } from './support'

// 위쪽 바 (11/14 사용자 요청, Quizlet식) — 가운데 덱 검색, + 만들기 메뉴, 오른쪽 위 프로필 메뉴·언어.
// 왼쪽 사이드바는 메뉴·폴더만, 폰에서는 검색창 대신 돋보기 + 아래 탭바.

/**
 * 연관 검색 목록의 '덱 줄'만 — 맨 아래 '모든 결과 보기' 줄은 뺀다.
 * 그 줄에도 검색어가 들어 있어서, option 첫 줄로 기다리면 내 덱 목록이 도착하기 전에 그 줄에 걸려 통과한다 (11/15 실제로 엉뚱한 줄을 눌렀다).
 */
const deckRows = (page: Page) => page.locator('.suggest-item:not(.suggest-all)')

test('위쪽 바 검색 — 내 덱을 이름으로 찾아 들어간다', async ({ page, request }) => {
  const label = `찾기${Date.now()}`
  const deckId = await makeEmptyDeck(request, label)

  await page.goto('/app/')
  const search = page.getByRole('combobox', { name: '덱 검색' })
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

test('연관 검색 — 치는 동안 맞는 내 덱이 뜨고, ↓·Enter로 바로 연다', async ({ page, request }) => {
  const label = `연관${Date.now()}`
  const deckId = await makeEmptyDeck(request, label)

  await page.goto('/app/')
  const box = page.getByRole('combobox', { name: '덱 검색' })
  await box.click()
  await box.fill(label)
  await expect(deckRows(page).first()).toContainText(label) // 첫 줄 = 이름이 맞는 내 덱
  await expect(page.getByRole('option', { name: /모든 결과 보기/ })).toBeVisible()

  await box.press('ArrowDown') // 첫 줄 = 이름이 맞는 내 덱
  await box.press('Enter')
  await expect(page).toHaveURL(new RegExp(`/app/decks/${deckId}$`))
})

test('연관 검색 — 줄을 고른 뒤 Esc로 닫고 Enter면 고른 덱이 아니라 검색 화면', async ({ page, request }) => {
  const label = `닫기${Date.now()}`
  await makeEmptyDeck(request, label)

  await page.goto('/app/')
  const box = page.getByRole('combobox', { name: '덱 검색' })
  await box.click()
  await box.fill(label)
  await expect(deckRows(page).first()).toContainText(label)
  await box.press('ArrowDown') // 덱 줄을 고른 채로
  await box.press('Escape') // 목록을 닫으면 고른 줄도 풀린다
  await expect(page.getByRole('listbox')).toHaveCount(0)
  await box.press('Enter')
  await expect(page).toHaveURL(/\/app\/search\?q=/)
})

test('연관 검색 — 고른 직후 늦게 온 공개 덱 결과가 목록을 바꿔도 고른 줄이 다른 덱으로 바뀌지 않는다', async ({ page, request }) => {
  const label = `지연${Date.now()}`
  await makeEmptyDeck(request, label)
  const fake = (id: number, title: string) => ({
    content: [{ id, title, description: '', authorNickname: '누군가', cardCount: 1, likeCount: 0, copyCount: 0, likedByMe: false, mine: false }],
    totalPages: 1,
    totalElements: 1,
    number: 0,
    size: 10,
  })
  // 첫 검색어의 공개 덱은 바로, 바꾼 검색어의 공개 덱은 '풀어 줄 때까지' 늦게 온다
  let calls = 0
  let release: () => void = () => {}
  const late = new Promise<void>((r) => (release = r))
  await page.route('**/public/decks?**', async (route) => {
    calls++
    if (calls === 1) return route.fulfill({ json: fake(900000001, `${label} 옛 공개`) })
    await late
    return route.fulfill({ json: fake(900000002, `${label}z 새 공개`) })
  })

  await page.goto('/app/')
  const box = page.getByRole('combobox', { name: '덱 검색' })
  await box.click()
  await box.fill(label)
  await expect(page.getByRole('option', { name: /옛 공개/ })).toBeVisible()

  await box.fill(`${label}z`) // 검색어를 바꾸면 옛 공개 덱은 바로 치운다 — 남은 줄 = '모든 결과 보기'
  await expect(page.getByRole('option', { name: /옛 공개/ })).toHaveCount(0)
  await box.press('ArrowDown') // '모든 결과 보기'를 고른다
  release() // 이제 새 공개 덱이 도착 → 목록 [새 공개, 모든 결과 보기]
  await expect(page.getByRole('option', { name: /새 공개/ })).toBeVisible()
  await expect(page.getByRole('option', { name: /모든 결과 보기/ })).toHaveAttribute('aria-selected', 'true')
  await box.press('Enter')
  await expect(page).toHaveURL(/\/app\/search\?q=/) // 번호로 기억했다면 같은 자리의 '새 공개' 덱이 열렸다
})

test('탐색 — 검색어를 달고 들어왔다가 메뉴 탐색으로 다시 오면 검색이 비워진다', async ({ page }) => {
  await page.goto('/app/explore?q=zzzz')
  const input = page.getByRole('textbox', { name: '공개 덱 검색' })
  await expect(input).toHaveValue('zzzz')
  await page.getByRole('navigation', { name: '주 메뉴' }).getByRole('link', { name: '탐색' }).click()
  await expect(page).toHaveURL(/\/app\/explore$/)
  await expect(input).toHaveValue('')
})

test('연관 검색 — 마우스가 올라가 있기만 한 줄은 Enter의 행선지가 아니다', async ({ page, request }) => {
  const label = `마우스${Date.now()}`
  await makeEmptyDeck(request, label)

  await page.goto('/app/')
  const box = page.getByRole('combobox', { name: '덱 검색' })
  await box.click()
  await box.fill(label)
  const row = deckRows(page).first()
  await expect(row).toContainText(label)
  await row.hover() // 올리기만 하고 누르지 않는다
  await box.press('Enter')
  await expect(page).toHaveURL(/\/app\/search\?q=/) // 화살표로 고르지 않았으니 그 덱이 아니라 검색 화면
})

test('연관 검색 — Tab으로 검색창을 떠나면 목록이 닫힌다', async ({ page, request }) => {
  await makeEmptyDeck(request, `탭${Date.now()}`)

  await page.goto('/app/')
  const box = page.getByRole('combobox', { name: '덱 검색' })
  await box.click()
  await expect(page.getByRole('listbox')).toBeVisible()
  await box.press('Tab') // 다음 칸(+ 버튼)으로 — 예전엔 목록이 열린 채 남아 + 메뉴와 겹쳐 열렸다
  await expect(page.getByRole('listbox')).toHaveCount(0)
})

test('덱 화면 — 위쪽 검색으로 다른 덱으로 가면 앞 덱의 수정 칸이 남지 않는다', async ({ page, request }) => {
  const stamp = Date.now()
  const idA = await makeEmptyDeck(request, `앞덱${stamp}`)
  const idB = await makeEmptyDeck(request, `뒷덱${stamp}`)

  await page.goto(`/app/decks/${idA}`)
  await page.getByRole('button', { name: '덱 제목·설명 수정' }).click() // 수정 칸을 연 채로 (저장·취소 안 함)
  await expect(page.getByLabel('덱 이름')).toBeVisible()

  const box = page.getByRole('combobox', { name: '덱 검색' })
  await box.click()
  await box.fill(`뒷덱${stamp}`)
  const target = deckRows(page).first() // 내 덱 목록이 도착해 '덱 줄'이 뜬 뒤에 누른다
  await expect(target).toContainText(`뒷덱${stamp}`)
  await target.click()
  await expect(page).toHaveURL(new RegExp(`/app/decks/${idB}$`))

  // 예전엔 앞 덱의 제목이 든 수정 칸이 그대로 열려 있어, 저장하면 뒷덱의 제목을 덮어썼다
  await expect(page.getByLabel('덱 이름')).toHaveCount(0)
  await expect(page.getByRole('heading', { level: 1 })).toContainText(`뒷덱${stamp}`)
})

test('메뉴 키보드 — ↑↓·Home으로 항목을 옮기고, Esc로 닫으면 포커스가 여는 버튼으로 돌아온다', async ({ page }) => {
  await page.goto('/app/')
  const plus = page.getByRole('button', { name: '새로 만들기' })
  await plus.focus()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('menu')).toBeVisible()

  await page.keyboard.press('ArrowDown') // 여는 버튼에서 ↓ = 첫 항목
  await expect(page.getByRole('menuitem', { name: '새 덱' })).toBeFocused()
  await page.keyboard.press('ArrowUp') // 첫 항목에서 ↑ = 마지막 항목으로 돈다
  await expect(page.getByRole('menuitem', { name: '새 폴더' })).toBeFocused()
  await page.keyboard.press('Home')
  await expect(page.getByRole('menuitem', { name: '새 덱' })).toBeFocused()

  await page.keyboard.press('Escape') // 예전엔 항목이 사라지면서 포커스가 문서 맨 위로 떨어졌다
  await expect(page.getByRole('menu')).toHaveCount(0)
  await expect(plus).toBeFocused()
})

test('연관 검색 — 화살표로 줄을 골라도 뒤의 화면은 스크롤되지 않는다', async ({ page, request }) => {
  const label = `스크롤${Date.now()}`
  await makeEmptyDeck(request, label)
  await page.setViewportSize({ width: 1280, height: 500 })
  await page.goto('/app/decks')
  await page.waitForLoadState('networkidle')
  await page.evaluate(() => window.scrollTo(0, 300))
  const before = await page.evaluate(() => window.scrollY)
  expect(before, '화면이 스크롤될 만큼 길다').toBeGreaterThan(100)

  const box = page.getByRole('combobox', { name: '덱 검색' })
  await box.click()
  await box.fill(label)
  await expect(deckRows(page).first()).toContainText(label)
  await box.press('ArrowDown') // 고른 줄을 목록 안에서 보이게 맞추는 동작이 뒤 화면까지 움직이면 안 된다
  await box.press('ArrowDown')
  await expect(page.getByRole('option', { name: /모든 결과 보기/ })).toBeVisible()
  expect(await page.evaluate(() => window.scrollY)).toBe(before)
})
