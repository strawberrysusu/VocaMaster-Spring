import { randomUUID } from 'node:crypto'
import type { Page } from '@playwright/test'
import { test, expect } from './support'

// 다국어 (ADR-056): 일본어·영어로 바꾸면 화면에 한국어 문구가 하나도 남지 않는다.
// 다른 테스트가 만든 덱(한국어 제목)이 섞이지 않게 이 테스트만의 새 계정을 만들고, 덱·카드에도 한글을 쓰지 않는다.
// 남아도 되는 한글은 언어 고르기의 '한국어' 하나뿐

const HANGUL = /[가-힣]/

async function freshAccount(page: Page): Promise<{ token: string; deckId: number }> {
  const res = await page.request.post('/auth/register', {
    data: { email: `e2e-lang-${Date.now()}@example.test`, password: randomUUID(), nickname: 'lang' },
  })
  expect(res.ok(), await res.text()).toBeTruthy()
  const { accessToken } = (await res.json()) as { accessToken: string }
  // 이 탭이 열 때마다 새 계정의 토큰을 쓴다 (setup 계정 토큰을 덮어씀)
  await page.addInitScript((tok) => localStorage.setItem('vm.accessToken', tok), accessToken)
  const deck = await page.request.post('/decks/import-file', {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: { title: 'Lang check', text: ['apple|りんご', '会議|かいぎ|meeting'].join('\n') },
  })
  expect(deck.ok(), await deck.text()).toBeTruthy()
  return { token: accessToken, deckId: ((await deck.json()) as { deckId: number }).deckId }
}

async function chooseLang(page: Page, lang: 'ja' | 'en' | 'zh' | 'ko') {
  await page.goto('/app/settings')
  await page.getByLabel('Language').selectOption(lang) // 고르면 새로고침
  await expect(page.locator('html')).toHaveAttribute('lang', lang === 'zh' ? 'zh-CN' : lang)
}

/** 화면 글자에서 한글을 찾는다 — 언어 고르기의 '한국어'만 빼고 */
async function koreanLeft(page: Page): Promise<string[]> {
  const text = await page.evaluate(() => document.body.innerText)
  return text
    .replaceAll('한국어', '')
    .split('\n')
    .filter((line) => HANGUL.test(line))
    .slice(0, 8)
}

for (const lang of ['ja', 'en', 'zh'] as const) {
  test(`언어 ${lang}: 주요 화면에 한국어 문구가 남지 않는다`, async ({ page }) => {
    test.setTimeout(120_000)
    const { deckId } = await freshAccount(page)
    await chooseLang(page, lang)

    const paths = [
      '/app/',
      '/app/decks',
      `/app/decks/${deckId}`,
      `/app/decks/${deckId}/import`,
      '/app/import-files',
      `/app/test/${deckId}?scope=all&count=all&shuffle=0`,
      `/app/typing/${deckId}`,
      `/app/quiz/${deckId}`,
      `/app/listening/${deckId}`,
      `/app/study?deckId=${deckId}`,
      '/app/stats',
      '/app/settings',
      '/app/explore',
    ]
    const bad: string[] = []
    for (const path of paths) {
      await page.goto(path)
      await page.waitForLoadState('networkidle')
      for (const line of await koreanLeft(page)) bad.push(`${path}: ${line}`)
    }

    // 눌러야 나오는 글자도: 시험지 창, 온라인 시험 채점 결과
    await page.goto(`/app/decks/${deckId}`)
    await page.locator('.mode-buttons button').last().click()
    await expect(page.getByRole('dialog')).toBeVisible()
    for (const line of await koreanLeft(page)) bad.push(`시험지 창: ${line}`)

    await page.goto(`/app/test/${deckId}?scope=all&count=all&shuffle=0`)
    await page.locator('.answer-buttons button').first().click() // 채점 (전부 비움)
    await expect(page.locator('.otest-summary')).toBeVisible()
    await page.waitForLoadState('networkidle')
    for (const line of await koreanLeft(page)) bad.push(`온라인 시험 결과: ${line}`)

    expect(bad).toEqual([])
  })
}

test('언어: 한국어로 되돌리면 예전 화면 그대로', async ({ page }) => {
  await freshAccount(page)
  await chooseLang(page, 'en')
  await chooseLang(page, 'ko')
  await page.goto('/app/decks')
  await expect(page.getByRole('heading', { name: '내 덱' })).toBeVisible()
})

// 서버가 보내는 오류 문구(한국어)도 고른 언어로 (tServer) — 로그인 안 한 새 브라우저에서 틀린 비밀번호로
test.describe('서버 오류 문구', () => {
  test.use({ storageState: { cookies: [], origins: [] } })
  for (const lang of ['ja', 'en', 'zh'] as const) {
    test(`언어 ${lang}: 로그인 실패 문구가 그 언어로`, async ({ page, errors }) => {
      await page.goto('/app/login')
      await page.getByLabel('Language').selectOption(lang) // 고르면 새로고침
      await expect(page.locator('html')).toHaveAttribute('lang', lang === 'zh' ? 'zh-CN' : lang)
      await page.locator('.auth-page input').nth(0).fill(`nobody-${Date.now()}@example.test`)
      await page.locator('.auth-page input').nth(1).fill('wrong-password-123')
      await page.locator('.auth-page button.primary').click()
      const alert = page.getByRole('alert')
      await expect(alert).toBeVisible()
      const text = (await alert.textContent()) ?? ''
      expect(text.trim().length).toBeGreaterThan(0)
      expect(text, '한국어가 남지 않아야 함').not.toMatch(HANGUL)
      errors.length = 0 // 로그인 실패 응답(401)의 콘솔 오류는 일부러 낸 것
    })
  }
})
