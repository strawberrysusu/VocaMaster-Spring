import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// dev: React(5173)가 Spring(8080)으로 API를 프록시 — CORS 설정 없이 same-origin처럼 동작.
// 운영 번들(Spring static 통합)은 별도 단계에서 NewsPick 방식으로 붙인다.
const backend = 'http://localhost:8080'
const apiPrefixes = ['/auth', '/decks', '/cards', '/public', '/reviews', '/quiz', '/typing', '/study', '/stats']

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: Object.fromEntries(apiPrefixes.map((p) => [p, { target: backend, changeOrigin: false }])),
  },
})
