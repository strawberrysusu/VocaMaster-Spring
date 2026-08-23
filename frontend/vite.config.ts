import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// React 화면은 /app/** 네임스페이스 — /decks 같은 API 경로와의 충돌 제거 (F5·북마크·직접 입력 전부 안전).
// 빌드 결과는 Spring static/app 으로 들어가 jar 하나로 서빙된다 (SpaConfig가 딥링크 fallback 담당).
const backend = 'http://localhost:8080'
const apiPrefixes = ['/auth', '/decks', '/cards', '/public', '/reviews', '/quiz', '/typing', '/study', '/stats', '/import']

export default defineConfig({
  base: '/app/',
  plugins: [react()],
  server: {
    proxy: Object.fromEntries(apiPrefixes.map((p) => [p, { target: backend, changeOrigin: false }])),
  },
  build: {
    outDir: '../src/main/resources/static/app',
    emptyOutDir: true,
  },
})
