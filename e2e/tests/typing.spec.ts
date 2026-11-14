import { test, expect, makeDeck, makeEmptyDeck, addCard } from './support'

// 타이핑 채점(ADR-051): 괄호 설명과 n. v. 같은 품사 표시는 안 쳐도 정답

test('타이핑: 괄호 설명과 품사 표시를 빼고 쳐도 정답', async ({ page, request }) => {
  const deckId = await makeDeck(request, '타이핑', ['apple|n. 사과 (과일)'])
  await page.goto(`/app/typing/${deckId}`)
  await page.getByRole('button', { name: '시작' }).click()

  const input = page.getByLabel('답 입력')
  await input.fill('사과')
  await input.press('Enter')
  await expect(page.getByText('정답!')).toBeVisible()
})

// 11/14 검토: 정답 끝에 눈에 안 보이는 얇은 공백(U+2009)이 붙어 있으면 타이핑(서버 채점)만 오답이었다 —
// 서버 Java trim()은 그 글자를 안 지우고 화면 JS trim()은 지워서. 이제 두 쪽이 같은 공백 목록을 쓴다
test('타이핑: 정답 끝에 눈에 안 보이는 공백이 붙어 있어도 정답', async ({ page, request }) => {
  const thinSpace = String.fromCharCode(0x2009) // 얇은 공백 — 복사·붙여넣기로 숨어 들어오는 글자
  const deckId = await makeEmptyDeck(request, '타이핑공백')
  await addCard(request, deckId, { front: 'meeting', back: '회의' + thinSpace })
  await page.goto(`/app/typing/${deckId}`)
  await page.getByRole('button', { name: '시작' }).click()

  const input = page.getByLabel('답 입력')
  await input.fill('회의')
  await input.press('Enter')
  await expect(page.getByText('정답!')).toBeVisible()
})
