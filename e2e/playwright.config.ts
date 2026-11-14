import fs from 'node:fs'
import path from 'node:path'
import { defineConfig, devices } from '@playwright/test'
import { APP_LOG, AUTH_FILE } from './paths'

// 화면 회귀 테스트 — 앱 jar를 빈 MySQL·Redis에 띄우고 진짜 Chromium으로 주요 흐름을 누른다.
// 계정·덱은 테스트가 매번 새로 만든다. 운영 서버·운영 데이터는 쓰지 않는다.
//   CI  : .github/workflows/ci.yml 의 e2e 잡 — 실패하면 배포(deploy)가 돌지 않는다
//   로컬: e2e/README.md

const port = Number(process.env.E2E_PORT ?? 8080)
const baseURL = `http://localhost:${port}`

function appJar(): string {
  const dir = process.env.E2E_JAR_DIR ?? path.resolve(__dirname, '../build/libs')
  const jar = fs.existsSync(dir) ? fs.readdirSync(dir).find((f) => f.endsWith('-SNAPSHOT.jar')) : undefined
  if (!jar) throw new Error(`앱 jar가 없어요 (${dir}) — 먼저 ./gradlew bootJar`)
  return path.join(dir, jar)
}

export default defineConfig({
  testDir: './tests',
  outputDir: './test-results',
  timeout: 60_000,
  globalTimeout: 15 * 60_000, // CI 잡 제한(20분) 안에서 끝나야 실패 자료(앱 로그·trace)가 남는다
  expect: { timeout: 10_000 },
  workers: 1, // 계정 하나를 나눠 쓰니 차례로
  retries: process.env.CI ? 1 : 0, // CI 일시 오류만 한 번 더 — 다시 해서 통과하면 보고서에 flaky로 남는다
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL,
    locale: 'ko-KR',
    timezoneId: 'Asia/Seoul',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'setup', testMatch: /auth\.setup\.ts/ },
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], storageState: AUTH_FILE },
      dependencies: ['setup'],
    },
  ],
  // 앱을 직접 띄우고 끝나면 내린다. DB·Redis 주소는 환경변수(SPRING_DATASOURCE_URL 등)로 받는다.
  // 이미 띄운 앱에 붙일 때만 E2E_SKIP_SERVER=1
  webServer: process.env.E2E_SKIP_SERVER
    ? undefined
    : {
        command: `java -jar "${appJar()}" --server.port=${port} > "${APP_LOG}" 2>&1`,
        url: `${baseURL}/app/`,
        timeout: 180_000,
        reuseExistingServer: false,
      },
})
