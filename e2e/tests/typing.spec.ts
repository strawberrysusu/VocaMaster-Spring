import { test, expect, makeDeck } from './support'

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
