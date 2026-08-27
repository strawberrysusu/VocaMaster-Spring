// 인기 정렬 부하 측정 (Phase 7 ⑧, ADR-041) — 한국 → 도쿄 실서버, 실사용 경로(HTTPS+nginx)
// 비교 대상: Redis ZSET 랭킹 (ranking.popular.enabled=true) vs DB 폴백 (false, Phase 5 이전 경로)
// 사용: k6 run tools/k6/popular.js          (기본: 실서버)
//       k6 run -e BASE=http://localhost:8083 tools/k6/popular.js
import http from 'k6/http'
import { check, sleep } from 'k6'

export const options = {
  stages: [
    { duration: '15s', target: 10 },   // 워밍업 — JIT·커넥션 풀 예열 (수치 오염 방지)
    { duration: '45s', target: 10 },   // 측정 구간 (VU 10 — 1GB 무료 서버라 예의 있는 부하)
  ],
  thresholds: { http_req_failed: ['rate<0.01'] },
  summaryTrendStats: ['avg', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
}

const BASE = __ENV.BASE || 'https://vocamaster-app.duckdns.org'

export default function () {
  const res = http.get(`${BASE}/public/decks?sort=popular`)
  check(res, { 'status 200': (r) => r.status === 200 })
  sleep(0.3)
}
