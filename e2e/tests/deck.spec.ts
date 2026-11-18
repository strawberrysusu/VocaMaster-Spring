import fs from 'node:fs'
import { test, expect, makeDeck, makeEmptyDeck, addCard, listCards, toggleStar, isStarred } from './support'

// 덱 화면·학습 카드의 설명 줄(ADR-054)과 Word 시험지(ADR-052)

test('덱 화면: 설명 줄이 뜻 아래 보이고, 추가 줄에서 설명까지 넣을 수 있다', async ({ page, request }) => {
  const deckId = await makeDeck(request, '설명', ['感謝|かんしゃ|감사'])
  await addCard(request, deckId, {
    front: '滅私奉公',
    reading: 'めっしほうこう',
    back: '멸사봉공',
    memo: '사사로운 이익을 버리고 나라·조직을 위해 힘씀',
  })
  await page.goto(`/app/decks/${deckId}`)
  await expect(page.locator('.word-memo', { hasText: '사사로운 이익을 버리고' })).toBeVisible()

  const add = page.locator('.create-row')
  await add.getByLabel('단어').fill('運輸')
  await add.getByLabel('읽기').fill('うんゆ')
  await add.getByLabel('뜻').fill('운수')
  await add.getByLabel('설명').fill('사람·화물을 실어 나름')
  await add.getByRole('button', { name: '추가' }).click()
  await expect(page.locator('.word-memo', { hasText: '사람·화물을 실어 나름' })).toBeVisible()

  const saved = (await listCards(request, deckId)).find((c) => c.front === '運輸')
  expect(saved).toMatchObject({ reading: 'うんゆ', back: '운수', memo: '사람·화물을 실어 나름' })
})

// 11/14 검토: 맥·아이폰 Safari는 입력기 변환을 확정하는 Enter를 keyCode 229 keydown으로 보낸다 —
// 그걸 '저장'으로 치면 설명을 다 쓰기 전에 카드가 저장됐다 (lib/keys.ts isPlainEnter)
test('덱 화면: 입력기 변환 확정 Enter로는 카드가 저장되지 않고, 진짜 Enter로 저장된다', async ({ page, request }) => {
  const deckId = await makeDeck(request, '입력기', ['感謝|かんしゃ|감사'])
  await page.goto(`/app/decks/${deckId}`)
  const add = page.locator('.create-row')
  await add.getByLabel('단어').fill('運輸')
  await add.getByLabel('뜻').fill('운수')
  await add.getByLabel('설명').fill('사람·화물을 실어 나름')
  await add.getByLabel('설명').dispatchEvent('keydown', { key: 'Enter', keyCode: 229 }) // Safari의 변환 확정 Enter
  await page.waitForTimeout(500)
  expect(await listCards(request, deckId)).toHaveLength(1)

  await add.getByLabel('설명').press('Enter')
  await expect(page.locator('.word-memo', { hasText: '사람·화물을 실어 나름' })).toBeVisible()
  expect(await listCards(request, deckId)).toHaveLength(2)
})

// 11/14 검토: 두 번째 클릭이 막 열린 창의 바깥 영역에 떨어져 창이 바로 닫혔다
test('시험지 버튼을 더블클릭해도 창이 열린 채로 있다', async ({ page, request }) => {
  const deckId = await makeDeck(request, '시험지더블', ['感謝|かんしゃ|감사'])
  await page.goto(`/app/decks/${deckId}`)
  await page.locator('.mode-buttons').getByRole('button', { name: /시험지/ }).dblclick()
  await page.waitForTimeout(500)
  await expect(page.getByRole('dialog', { name: '시험지 만들기' })).toBeVisible()
})

test('학습 카드: 뒤집으면 뜻 아래 설명 줄이 보인다', async ({ page, request }) => {
  const deckId = await makeEmptyDeck(request, '학습')
  await addCard(request, deckId, { front: '会議', reading: 'かいぎ', back: '회의', memo: '여럿이 모여 의논함' })
  await page.goto(`/app/study?deckId=${deckId}`)
  await page.locator('.study-card').click()
  await expect(page.locator('.study-memo')).toHaveText('여럿이 모여 의논함')
})

test('시험지: Word 파일이 단어가 들어간 채로 받아진다', async ({ page, request }) => {
  const deckId = await makeDeck(request, '시험지', ['感謝|かんしゃ|감사', 'ざら|아주 흔함'])
  await page.goto(`/app/decks/${deckId}`)
  await page.locator('.mode-buttons').getByRole('button', { name: /시험지/ }).click()
  const dialog = page.getByRole('dialog', { name: '시험지 만들기' })
  await expect(dialog).toBeVisible()

  const downloading = page.waitForEvent('download')
  await dialog.getByRole('button', { name: 'Word 파일 받기' }).click()
  const download = await downloading
  expect(download.suggestedFilename()).toMatch(/_시험지\.docx$/)

  const bytes = fs.readFileSync(await download.path())
  expect(bytes.subarray(0, 4).toString('hex'), 'zip 서명').toBe('504b0304')
  expect(bytes.includes(Buffer.from('word/document.xml'))).toBe(true)
  // 무압축 zip이라 본문 글자가 그대로 들어 있다 — 단어가 실제로 시험지에 실렸는지
  expect(bytes.includes(Buffer.from('感謝'))).toBe(true)
  expect(bytes.includes(Buffer.from('ざら'))).toBe(true)
})

// 학습 버튼 (11/14 사용자 요청) — '복습 학습 (Leitner)'·'⭐ 별표 복습' 두 버튼을 '낱말카드' 하나로: 누르면 범위를 고른다
test('낱말카드 — 누르면 카드 학습 / 전체 복습 / 별표만 복습 중에서 고른다', async ({ page, request }) => {
  const deckId = await makeDeck(request, '낱말카드메뉴', ['感謝|かんしゃ|감사', 'ざら|아주 흔함'])
  const [first] = await listCards(request, deckId)
  await toggleStar(request, first.id)

  await page.goto(`/app/decks/${deckId}`)
  await page.locator('.mode-buttons').getByRole('button', { name: '낱말카드' }).click()
  const menu = page.getByRole('menu')
  await expect(menu.getByRole('menuitem', { name: '카드 학습' })).toHaveAttribute('href', `/app/study?deckId=${deckId}&mode=browse`)
  await expect(menu.getByRole('menuitem', { name: '전체 복습' })).toHaveAttribute('href', `/app/study?deckId=${deckId}`)
  await menu.getByRole('menuitem', { name: '별표만 복습 (1장)' }).click()
  await expect(page).toHaveURL(new RegExp(`/app/study\\?deckId=${deckId}&starredOnly=1$`))
})

test('카드 학습 — 알아요·몰라요 없이 넘겨 보고, 서버에 아무것도 보내지 않는다', async ({ page, request }) => {
  const deckId = await makeDeck(request, '카드학습', ['感謝|かんしゃ|감사', 'ざら|아주 흔함'])
  const sent: string[] = []
  page.on('request', (r) => {
    if (r.url().includes('/reviews/')) sent.push(r.url())
  })

  await page.goto(`/app/study?deckId=${deckId}&mode=browse`)
  await expect(page.getByText('1 / 2 · 카드 학습')).toBeVisible()
  await expect(page.getByRole('button', { name: '알아요' })).toHaveCount(0)
  await page.keyboard.press('Space') // 뒤집기 — 카드 순서는 최신순이라 어느 카드든 뒷면(뜻)이 보이면 된다
  await expect(page.locator('.study-answer')).toBeVisible()
  await page.keyboard.press('KeyD') // 채점 키는 듣지 않는다 — 넘어가지 않는다
  await expect(page.getByText('1 / 2 · 카드 학습')).toBeVisible()
  await page.keyboard.press('ArrowRight')
  await page.keyboard.press('ArrowRight') // 마지막 카드를 지나면 마칠지 묻는다
  await expect(page.getByText('2장을 다 봤어요')).toBeVisible()
  await page.getByRole('button', { name: '학습 완료' }).click()
  await expect(page.getByRole('heading', { name: '카드 보기 완료' })).toBeVisible()
  expect(sent, '카드 학습은 복습 기록 API를 부르지 않는다').toEqual([])
})

// 11/16: 닉네임 저장과 같은 종류 — 카드 추가 응답이 오면 네 칸을 통째로 비워서, 느린 망에서 응답 전에 친 다음 단어가 지워졌다
test('덱 화면: 카드 추가 응답을 기다리는 동안 친 다음 단어는 지워지지 않는다', async ({ page, request }) => {
  const deckId = await makeEmptyDeck(request, '연속추가')
  await page.goto(`/app/decks/${deckId}`)
  const isAdd = (method: string, url: string) => method === 'POST' && new URL(url).pathname === `/decks/${deckId}/cards`

  let release: () => void = () => {}
  const held = new Promise<void>((resolve) => (release = resolve))
  await page.route(`**/decks/${deckId}/cards`, async (route) => {
    if (isAdd(route.request().method(), route.request().url())) await held // 추가 요청의 응답을 붙잡아 둔다
    await route.continue()
  })

  const add = page.locator('.create-row')
  await add.getByLabel('단어').fill('会議')
  await add.getByLabel('뜻').fill('회의')
  await add.getByLabel('뜻').press('Enter')
  await expect(add.getByRole('button', { name: '추가 중...' })).toBeVisible()
  await add.getByLabel('단어').fill('感謝') // 응답이 오기 전에 다음 카드의 단어를 친다
  const added = page.waitForResponse((r) => isAdd(r.request().method(), r.url()))
  release()
  await added

  await expect(add.getByRole('button', { name: '추가', exact: true })).toBeVisible() // 추가가 끝났다
  await expect(add.getByLabel('단어')).toHaveValue('感謝') // 예전엔 여기서 비워졌다
  await expect(add.getByLabel('뜻')).toHaveValue('') // 보낸 값 그대로였던 칸은 비운다
  expect((await listCards(request, deckId)).map((c) => c.front)).toEqual(['会議']) // 첫 카드는 저장됐다
})

// 11/18: 별표를 누르면 목록 전체를 다시 불러, 그동안 목록이 '불러오는 중' 문구로 바뀌며 스크롤이 맨 위로 돌아갔다
// (별표 74개를 끄다가 확인 · Codex 8차 검산도 125장 덱의 110번째 카드에서 재현) → 그 카드 한 줄만 바꾼다
test('긴 덱에서 별표를 켜고 꺼도 스크롤 위치가 그대로다', async ({ page, request }) => {
  const lines = Array.from({ length: 60 }, (_, i) => `word${i + 1}|뜻${i + 1}`)
  const deckId = await makeDeck(request, '별표스크롤', lines)
  await page.goto(`/app/decks/${deckId}`)
  await expect(page.getByText('카드 60장 · 별표 0장')).toBeVisible()

  const lastRow = page.locator('.row-actions').last()
  await lastRow.scrollIntoViewIfNeeded()
  const word = ((await lastRow.locator('.edit-btn').getAttribute('aria-label')) ?? '').replace(/ 수정$/, '')
  const star = lastRow.locator('.star-btn')
  const scrollY = () => page.evaluate(() => Math.round(window.scrollY))
  const starTop = async () => Math.round((await star.boundingBox())!.y)
  // 화면이 자리 잡은 뒤의 위치를 기준으로 삼는다 — 글꼴이 늦게 바뀌면 문서 높이가 조금 줄어 스크롤도 따라 움직인다
  // (처음엔 바로 쟀다가 별표와 상관없이 44px 달라져 실패했다)
  await page.evaluate(() => document.fonts.ready)
  let before = -1
  await expect
    .poll(async () => {
      const y = await scrollY()
      const settled = y === before
      before = y
      return settled
    }, { intervals: [250] })
    .toBe(true)
  expect(before).toBeGreaterThan(500) // 맨 아래 카드까지 내려와 있다
  const topBefore = await starTop()

  await star.click()
  await expect(star).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByText('카드 60장 · 별표 1장')).toBeVisible() // 별표 수도 다시 부르지 않고 맞게 바뀐다
  expect(Math.abs((await scrollY()) - before)).toBeLessThanOrEqual(1)
  expect(Math.abs((await starTop()) - topBefore)).toBeLessThanOrEqual(1) // 누른 단추가 화면에서 제자리다
  expect(await isStarred(request, deckId, word)).toBe(true)

  await star.click()
  await expect(star).toHaveAttribute('aria-pressed', 'false')
  await expect(page.getByText('카드 60장 · 별표 0장')).toBeVisible()
  expect(Math.abs((await scrollY()) - before)).toBeLessThanOrEqual(1)
  expect(Math.abs((await starTop()) - topBefore)).toBeLessThanOrEqual(1)
  expect(await isStarred(request, deckId, word)).toBe(false)
})
