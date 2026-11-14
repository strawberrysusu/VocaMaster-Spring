import { test, expect, makeEmptyDeck, addCard, overflowX, LONG } from './support'

// 화면 깨짐 회귀: 11/14에 손으로 찾았던 두 가지
//   ① 1024px에서 모드 버튼 5개가 '타/이/핑'처럼 세로로 깨짐 (PR #23)
//   ② 덱 화면 카드 추가 줄이 가로로 179px 넘침 (PR #25)
// 를 다시는 손으로 찾지 않게, 폭 4개 × 주요 화면에서 가로 넘침 0을 확인한다

const WIDTHS = [375, 820, 1024, 1280]

for (const width of WIDTHS) {
  test(`가로 넘침 없음 — ${width}px`, async ({ page, request }) => {
    const deckId = await makeEmptyDeck(request, `레이아웃${width}`)
    await addCard(request, deckId, { front: LONG.word, reading: LONG.reading, back: LONG.back, memo: LONG.memo })
    await addCard(request, deckId, { front: '会議', reading: 'かいぎ', back: '회의' })
    await page.setViewportSize({ width, height: 900 })

    const paths = [
      '/app/',
      '/app/decks',
      `/app/decks/${deckId}`,
      `/app/decks/${deckId}/import`,
      `/app/study?deckId=${deckId}`,
      `/app/test/${deckId}?scope=all&count=all&shuffle=0`,
      `/app/typing/${deckId}`,
      `/app/quiz/${deckId}`,
      `/app/listening/${deckId}`,
      '/app/import-files',
      '/app/explore',
      '/app/stats',
      '/app/ranking',
      '/app/settings',
    ]
    const bad: string[] = []
    for (const path of paths) {
      await page.goto(path)
      await page.waitForLoadState('networkidle')
      const over = await overflowX(page)
      if (over > 0) bad.push(`${path} 가로 ${over}px 넘침`)
    }

    // 덱 화면 모드 버튼: 한 줄 글씨 그대로 (세로로 깨지거나 글씨가 버튼 밖으로 나가지 않음)
    await page.goto(`/app/decks/${deckId}`)
    await expect(page.locator('.mode-buttons > *').first()).toBeVisible()
    const broken = await page.locator('.mode-buttons > *').evaluateAll((els) =>
      els
        .filter((e) => e.scrollWidth > e.clientWidth + 1 || e.getBoundingClientRect().height > 64)
        .map((e) => `${e.textContent?.trim()} (${Math.round(e.getBoundingClientRect().width)}×${Math.round(e.getBoundingClientRect().height)})`),
    )
    for (const b of broken) bad.push(`모드 버튼 깨짐: ${b}`)

    expect(bad).toEqual([])
  })
}

// 띄어쓰기 없는 긴 영문·숫자 제목 (파일 이름이 그대로 덱 이름이 되는 'txt로 덱 만들기'에서 생길 수 있다).
// 11/14 이 테스트가 처음 찾았다(PR #26에서 test.fail로 재현: 덱 목록 820px 578px·덱 화면·온라인 시험 폰 173px·홈 폰 114px 넘침)
// → PR #27에서 고치고 표시를 뗐다
test('띄어쓰기 없는 긴 덱 제목도 가로로 넘치지 않는다', async ({ page, request }) => {
  const long = 'Internationalization_vocabulary_2026'
  const deckId = await makeEmptyDeck(request, long)
  await makeEmptyDeck(request, long) // 덱 목록 한 줄(3칸)을 긴 제목으로 채운다
  await makeEmptyDeck(request, long)
  await addCard(request, deckId, { front: '会議', reading: 'かいぎ', back: '회의' })
  const checks: [number, string][] = [
    [820, '/app/decks'],
    [1024, '/app/decks'],
    [1280, '/app/decks'],
    [375, '/app/decks'],
    [375, `/app/decks/${deckId}`],
    [820, `/app/decks/${deckId}`],
    [375, `/app/test/${deckId}?scope=all&count=all&shuffle=0`],
    [375, `/app/study?deckId=${deckId}`], // 여기서 '최근 학습 덱'에 올라가고
    [375, `/app/typing/${deckId}`],
    [375, `/app/quiz/${deckId}`],
    [375, `/app/listening/${deckId}`],
    [375, '/app/stats'],
    [375, '/app/'], // 홈의 최근 학습 덱 줄
    [1280, '/app/'],
  ]
  const bad: string[] = []
  for (const [width, path] of checks) {
    await page.setViewportSize({ width, height: 900 })
    await page.goto(path)
    await page.waitForLoadState('networkidle')
    const over = await overflowX(page)
    if (over > 0) bad.push(`${width}px ${path} 가로 ${over}px 넘침`)
  }
  expect(bad).toEqual([])
})

// 폰에서 학습 카드는 뒤집어도 한 화면 (PR #10·#11) — 실제 단어장의 가장 긴 조합 두 가지
for (const [label, back, memo] of [
  ['긴 뜻 + 긴 설명', LONG.back, LONG.memo],
  ['짧은 뜻 + 아주 긴 설명', '찰, 표', LONG.memo64],
] as const) {
  for (const size of [
    { width: 375, height: 667 },
    { width: 390, height: 844 },
  ]) {
    test(`학습 카드 한 화면 — ${size.width}×${size.height}, ${label}`, async ({ page, request }) => {
      await page.setViewportSize(size)
      const deckId = await makeEmptyDeck(request, '학습폰')
      await addCard(request, deckId, { front: LONG.word, reading: LONG.reading, back, memo })
      await page.goto(`/app/study?deckId=${deckId}`)
      await page.locator('.study-card').click()
      await expect(page.locator('.study-memo')).toBeVisible()
      const extra = await page.evaluate(() => document.documentElement.scrollHeight - window.innerHeight)
      expect(extra, '세로 스크롤 없이 한 화면').toBeLessThanOrEqual(1)
      expect(await overflowX(page)).toBe(0)
    })
  }
}
