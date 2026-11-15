import { test, expect, makeEmptyDeck, addCard, makeFolder, moveToFolder, setDeckDescription, useLang, overflowX, LONG } from './support'

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
  // 설명에 띄어쓰기 없는 긴 주소 — 덱 카드의 설명 줄이 카드 밖으로 나가 가로로 넘쳤다 (11/15 점검)
  await setDeckDescription(request, deckId, `https://example.test/${'vocabulary-list-'.repeat(8)}end`)
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

// 낱말카드 메뉴는 버튼 왼쪽 끝에 맞춰 펴고, 오른쪽에 자리가 없으면 왼쪽으로 편다 (DeckDetail이 재서 정함, 11/15 점검).
// 버튼 줄이 오른쪽 정렬이라, 창이 좁거나 덱 제목이 길어 버튼이 오른쪽 끝에 몰리면 메뉴 오른쪽 약 90px가 화면 밖으로 나갔다
for (const [width, title] of [
  [780, '메뉴넘침'],
  [820, 'Internationalization_vocabulary_2026_with_a_very_long_title'], // 긴 제목 → 버튼이 오른쪽 끝에 한 줄로 몰린다
  [1024, 'Internationalization_vocabulary_2026_with_a_very_long_title'],
  [1280, '메뉴넘침'],
  [375, '메뉴넘침'],
  [320, '메뉴넘침'],
] as const) {
  test(`낱말카드 메뉴를 펼쳐도 화면 밖으로 나가지 않는다 — ${width}px, 제목 ${title.length}자`, async ({ page, request }) => {
    const deckId = await makeEmptyDeck(request, title)
    await addCard(request, deckId, { front: '会議', reading: 'かいぎ', back: '회의' })
    await page.setViewportSize({ width, height: 900 })
    await page.goto(`/app/decks/${deckId}`)
    await page.locator('.mode-buttons').getByRole('button', { name: '낱말카드' }).click()
    const menu = page.getByRole('menu')
    await expect(menu).toBeVisible()

    expect(await overflowX(page)).toBe(0)
    const box = (await menu.boundingBox())!
    expect(box.x, '메뉴 왼쪽 끝').toBeGreaterThanOrEqual(0)
    expect(box.x + box.width, '메뉴 오른쪽 끝').toBeLessThanOrEqual(width)
    // 메뉴의 네 귀퉁이 안쪽이 실제로 메뉴다 — 사이드바·위쪽 바·아래 탭바에 가려지지 않는다
    const covered = await menu.evaluate((el) => {
      const r = el.getBoundingClientRect()
      const pts = [
        [r.left + 6, r.top + 6],
        [r.right - 6, r.top + 6],
        [r.left + 6, r.bottom - 6],
        [r.right - 6, r.bottom - 6],
      ]
      return pts.filter(([x, y]) => !el.contains(document.elementFromPoint(x, y))).length
    })
    expect(covered, '다른 것에 가려진 귀퉁이 수').toBe(0)
  })
}

// 폰의 위쪽 바·아래 탭바는 position: fixed라 위 가로 넘침 검사(문서 너비)에 안 잡힌다 — 직접 잰다.
// 11/14에 랭킹 탭이 생겨 6칸이 된 뒤 일본어·영어는 320~360px 폰에서 글자가 두 줄로 깨지고(ホー/ム),
// 영어 320px는 '설정' 탭이 화면 밖으로 28px 나갔다 (11/15 점검에서 실측 → 탭을 같은 너비로 나눔)
for (const lang of ['ko', 'ja', 'en', 'zh'] as const) {
  test(`폰 고정 바 — ${lang}: 탭 6개가 한 줄 글씨로 화면 안에 들어가고, 위쪽 바도 화면 안`, async ({ page }) => {
    await useLang(page, lang)
    const bad: string[] = []
    for (const width of [320, 360, 375]) {
      await page.setViewportSize({ width, height: 700 })
      await page.goto('/app/')
      await expect(page.locator('.side-menu a')).toHaveCount(6)
      await page.waitForLoadState('networkidle')
      await page.evaluate(() => document.fonts.ready)

      const tabs = await page.locator('.side-menu a').evaluateAll((els) =>
        els.map((e) => {
          const cell = e.getBoundingClientRect()
          const range = document.createRange()
          range.selectNodeContents(e)
          return {
            text: (e.textContent ?? '').trim(),
            left: cell.left,
            right: cell.right,
            height: cell.height,
            cellWidth: cell.width,
            textWidth: range.getBoundingClientRect().width, // 아이콘 + 글자 중 넓은 쪽 = 글자 너비
          }
        }),
      )
      for (const tab of tabs) {
        const where = `${width}px '${tab.text}'`
        if (tab.height > 56) bad.push(`${where} 글자가 두 줄 (탭 높이 ${Math.round(tab.height)}px, 한 줄이면 49px)`)
        if (tab.left < -0.5 || tab.right > width + 0.5) bad.push(`${where} 화면 밖 (${Math.round(tab.left)}~${Math.round(tab.right)})`)
        if (tab.textWidth > tab.cellWidth + 4) bad.push(`${where} 글자가 옆 탭을 침범 (글자 ${Math.round(tab.textWidth)}px, 칸 ${Math.round(tab.cellWidth)}px)`)
      }
      const barOver = await page.locator('.side-menu').evaluate((e) => e.scrollWidth - e.clientWidth)
      if (barOver > 1) bad.push(`${width}px 아래 탭바 가로 ${barOver}px 넘침`)

      const top = await page.locator('.topbar > *, .top-actions > *').evaluateAll((els) =>
        els.map((e) => ({ name: e.className || e.tagName, left: e.getBoundingClientRect().left, right: e.getBoundingClientRect().right })),
      )
      for (const part of top)
        if (part.left < -0.5 || part.right > width + 0.5) bad.push(`${width}px 위쪽 바 '${part.name}' 화면 밖 (${Math.round(part.left)}~${Math.round(part.right)})`)
    }
    expect(bad).toEqual([])
  })
}

// 프로필 메뉴(260px)의 배지 + 가입일, 홈 '오늘의 랭킹' 카드 제목 — 한국어는 한 줄에 들어가지만
// 일본어·영어는 넘쳐서 배지 안 글자와 카드 제목이 두 줄로 깨졌다 (11/15 점검에서 실측 → 넘치면 뒤 요소를 다음 줄로)
for (const lang of ['ja', 'en'] as const) {
  test(`${lang}: 프로필 메뉴 배지와 홈 랭킹 카드 제목이 글자 중간에서 줄바꿈되지 않는다`, async ({ page }) => {
    await useLang(page, lang)
    await page.setViewportSize({ width: 1280, height: 800 })
    await page.goto('/app/')
    await page.locator('.avatar-btn').click()
    await expect(page.locator('.pp-meta')).toBeVisible()
    await page.evaluate(() => document.fonts.ready)
    const heights = await page.locator('.pp-meta > span').evaluateAll((els) => els.map((e) => Math.round(e.getBoundingClientRect().height)))
    expect(heights.length).toBe(2) // 배지 + 가입일
    for (const h of heights) expect(h, `배지·가입일 높이 ${heights.join(', ')} (한 줄이면 17~21px)`).toBeLessThan(28)
    const menu = (await page.locator('.profile-menu').boundingBox())!
    expect(menu.x + menu.width).toBeLessThanOrEqual(1280)

    await page.setViewportSize({ width: 1150, height: 800 }) // 3칸 줄에서 카드가 가장 좁은 구간(1101~1197px)
    await page.goto('/app/')
    const label = page.locator('.rank-card-head .label')
    await expect(label).toBeVisible()
    await page.evaluate(() => document.fonts.ready)
    expect((await label.boundingBox())!.height, '랭킹 카드 제목 높이 (한 줄이면 20px)').toBeLessThan(28)
    expect(await overflowX(page)).toBe(0)
  })
}

// 폴더 담기 창 — 화면 한가운데 뜨고 어두운 막이 화면 전체(위쪽 바·사이드바·아래 탭바 포함)를 덮는다.
// 예전엔 본문 칸(.shell)에 끝난 애니메이션의 transform이 남아 있어 창이 화면이 아니라 그 칸을 기준으로 떴다:
// 막이 바들을 못 덮고, 덱이 많아 칸이 화면보다 길면 창이 화면 밖(칸의 한가운데)에 떴다 (11/15 점검에서 실측)
test('폴더 담기 창 — 덱이 많은 폴더에서도 화면 안에 뜨고, 막이 위쪽 바·사이드바·아래 탭바까지 덮는다', async ({ page, request }) => {
  const folderId = await makeFolder(request, `담기${Date.now() % 1_000_000}`)
  for (let i = 0; i < 14; i++) await moveToFolder(request, await makeEmptyDeck(request, `폴더덱${i}`), folderId)
  await makeEmptyDeck(request, '담을덱') // 담을 후보가 하나는 있게

  for (const size of [
    { width: 375, height: 667 },
    { width: 1280, height: 720 },
  ]) {
    await page.setViewportSize(size)
    await page.goto(`/app/decks?folder=${folderId}`)
    await page.getByRole('button', { name: /이 폴더에 덱 담기/ }).click()
    const panel = page.getByRole('dialog')
    await expect(panel).toBeVisible()
    await page.waitForTimeout(400) // 본문 칸의 등장 애니메이션(0.3초)이 끝난 뒤의 모습을 본다

    const overlay = (await page.locator('.modal-overlay').boundingBox())!
    expect([overlay.x, overlay.y, overlay.width, overlay.height].map(Math.round), '어두운 막 = 화면 전체').toEqual([0, 0, size.width, size.height])
    const box = (await panel.boundingBox())!
    expect(box.y, '창 위쪽 끝').toBeGreaterThanOrEqual(0)
    expect(box.y + box.height, '창 아래쪽 끝').toBeLessThanOrEqual(size.height)
    // 위쪽 바 · 왼쪽 가장자리(넓은 화면의 사이드바 자리) · 맨 아래(폰의 탭바 자리)를 눌러도 막이 받는다
    const onOverlay = await page.evaluate(() => {
      const at = (x: number, y: number) => !!document.elementFromPoint(x, y)?.closest('.modal-overlay')
      return [at(innerWidth / 2, 20), at(8, innerHeight / 2), at(innerWidth / 2, innerHeight - 8)]
    })
    expect(onOverlay, `${size.width}px: 위쪽 바 / 왼쪽 가장자리 / 맨 아래`).toEqual([true, true, true])
    await page.keyboard.press('Escape')
  }
})
