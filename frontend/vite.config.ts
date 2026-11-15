import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// React 화면은 /app/** 네임스페이스 — /decks 같은 API 경로와의 충돌 제거 (F5·북마크·직접 입력 전부 안전).
// 빌드 결과는 Spring static/app 으로 들어가 jar 하나로 서빙된다 (SpaConfig가 딥링크 fallback 담당).
const backend = 'http://localhost:8080'
// 화면 코드가 부르는 API 주소의 첫 칸은 전부 여기 있어야 한다 — scripts/test-proxy.mjs가 검사 (11/14: /folders·/rankings·/users가 빠져 개발 서버에서 404)
const apiPrefixes = [
  '/auth', '/decks', '/cards', '/public', '/reviews', '/quiz', '/typing', '/study', '/stats', '/import',
  '/folders', '/rankings', '/users', '/oauth2',
]
// API는 아니지만 백엔드가 받아야 하는 주소 (11/16 Codex 5차 검산 — 이것도 scripts/test-proxy.mjs가 검사한다)
//  · /login/oauth2 — 구글 로그인에서 돌아오는 콜백(/login/oauth2/code/google). 아래 프록시가 changeOrigin: false라 백엔드는
//    제 주소를 localhost:5173으로 알고 구글에도 5173의 콜백 주소를 준다 → 여기 없으면 구글에서 돌아오는 순간 404.
//    (구글 콘솔의 '승인된 리디렉션 URI'에도 http://localhost:5173/login/oauth2/code/google 이 있어야 끝까지 된다)
//  · /privacy.html — 백엔드가 주는 정적 문서 (로그인·설정·프로필 메뉴의 링크)
const serverPaths = ['/login/oauth2', '/privacy.html']

export default defineConfig({
  base: '/app/',
  plugins: [react()],
  server: {
    proxy: Object.fromEntries([...apiPrefixes, ...serverPaths].map((p) => [p, { target: backend, changeOrigin: false }])),
  },
  build: {
    outDir: '../src/main/resources/static/app',
    emptyOutDir: true,
  },
})
