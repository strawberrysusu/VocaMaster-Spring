import fs from 'node:fs'
import { test, expect, makeDeck, makeEmptyDeck, addCard, listCards } from './support'

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
