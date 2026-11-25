import { test, expect, makeEmptyDeck } from './support'

// 11/25 Lighthouse(운영 /app/login, 데스크톱)가 찾은 것 — ADR-065
// 접근성: 본문 표시(main) 없음·버튼 글자 대비 3.35:1 / SEO: 페이지 설명 없음 / 성능: 구글 글꼴 CSS가 첫 그리기를 막음

const FONT_CSS = 'fonts.googleapis.com/css2'

/**
 * 글자색과 실제 배경(자신부터 위로 올라가며 처음 만나는 투명하지 않은 배경)의 대비 (WCAG 2 공식).
 * 브라우저 안에서 돈다 — 바깥 변수를 쓰지 말 것
 */
function contrastOnBackground(el: Element): number {
  const parts = (css: string) => (css.match(/[\d.]+/g) ?? []).map(Number)
  const lum = ([r, g, b]: number[]) => {
    const f = (v: number) => {
      const c = v / 255
      return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
    }
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)
  }
  let bg = [255, 255, 255]
  for (let cur: Element | null = el; cur; cur = cur.parentElement) {
    const c = parts(getComputedStyle(cur).backgroundColor)
    if (c.length >= 3 && (c.length < 4 || c[3] > 0)) {
      bg = c.slice(0, 3)
      break
    }
  }
  const fg = lum(parts(getComputedStyle(el).color))
  const b = lum(bg)
  return (Math.max(fg, b) + 0.05) / (Math.min(fg, b) + 0.05)
}

test.describe('로그인 화면 (로그인 전)', () => {
  test.use({ storageState: { cookies: [], origins: [] } })

  test('본문 표시(main)가 하나, 검색 결과에 쓰이는 페이지 설명이 있다', async ({ page }) => {
    await page.goto('/app/login')
    await expect(page.getByRole('main')).toHaveCount(1)
    await expect(page.locator('meta[name="description"]')).toHaveAttribute('content', /단어/)
  })

  test('글자 대비 — 「구글로 로그인」·「회원가입」 버튼이 4.5:1 이상', async ({ page }) => {
    await page.goto('/app/login')
    for (const name of ['구글로 로그인', '계정이 없어요 → 회원가입']) {
      const ratio = await page.getByRole('button', { name }).evaluate(contrastOnBackground)
      expect(ratio, name).toBeGreaterThanOrEqual(4.5)
    }
  })

  test('구글 글꼴 CSS는 첫 그리기를 막지 않는다 — 미리 받아 두고(preload), 다 받으면 적용', async ({ page, request }) => {
    // 서버가 보내는 HTML 그대로: 글꼴 CSS는 preload 하나뿐이고, stylesheet로 걸린 것은 없다
    const html = await (await request.get('/app/login')).text()
    const rels = await page.evaluate(
      ([h, font]) =>
        [...new DOMParser().parseFromString(h, 'text/html').querySelectorAll(`link[href*="${font}"]`)].map((l) =>
          l.getAttribute('rel'),
        ),
      [html, FONT_CSS] as const,
    )
    expect(rels).toEqual(['preload'])

    // 다 받으면 stylesheet로 바뀌어 적용된다 — CI 망에 기대지 않게 글꼴 CSS는 가짜로 바로 준다
    await page.route(`https://${FONT_CSS}**`, (route) => route.fulfill({ contentType: 'text/css', body: '/* e2e */' }))
    await page.goto('/app/login')
    await expect(page.locator(`link[href*="${FONT_CSS}"]`)).toHaveAttribute('rel', 'stylesheet')
  })
})

// ADR-066: 더 연한 회색(--muted-2, 날짜·카드 장수·학습 안내)도 4.5:1 이상 — 전에는 #9b9cab로 2.71:1
test('덱 화면의 연한 회색 글자(카드 장수)도 4.5:1 이상', async ({ page, request }) => {
  await makeEmptyDeck(request, `대비${Date.now()}`)
  await page.goto('/app/decks')
  const meta = page.locator('.deck-card .meta', { hasText: '카드 0장' }).first()
  await expect(meta).toBeVisible()
  expect(await meta.evaluate(contrastOnBackground)).toBeGreaterThanOrEqual(4.5)
})

test('로그인한 화면들도 본문 표시(main)가 하나씩', async ({ page }) => {
  for (const path of ['/app/', '/app/decks', '/app/explore', '/app/stats', '/app/ranking', '/app/settings']) {
    await page.goto(path)
    await expect(page.getByRole('main'), path).toHaveCount(1)
  }
})
