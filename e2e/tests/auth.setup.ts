import { randomUUID } from 'node:crypto'
import { test as setup, expect } from '@playwright/test'
import { AUTH_FILE } from '../paths'

// 실행마다 새 계정 — 빈 테스트 DB에만 생긴다. 비밀번호도 매번 무작위라 실제 계정과 무관
setup('새 계정을 만들고 로그인 화면으로 들어간다', async ({ page }) => {
  const email = `e2e-${Date.now()}@example.test`
  const password = randomUUID()
  const res = await page.request.post('/auth/register', { data: { email, password, nickname: 'e2e' } })
  expect(res.ok(), await res.text()).toBeTruthy()
  await page.context().clearCookies() // 가입이 심은 쿠키를 지우고, 로그인 화면 자체를 거친다

  await page.goto('/app/login')
  await page.getByPlaceholder('이메일').fill(email)
  await page.getByPlaceholder('비밀번호 (6자 이상)').fill(password)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page).toHaveURL(/\/app\/?$/)
  await page.context().storageState({ path: AUTH_FILE })
})
