// 정밀 측정용 (Codex 검산 반영, 8/28): stages 없이 정상 상태만 잰다.
// 사용: 워밍업은 이 스크립트를 DURATION=30s로 먼저 1회 돌리고 결과를 버린 뒤,
//       본 측정을 DURATION=60s로 실행 — "워밍업 수치 제외"가 코드로 보장된다.
import http from 'k6/http'
import { check, sleep } from 'k6'

export const options = {
  vus: 10,
  duration: __ENV.DURATION || '60s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],   // 속도 합격선 (1GB 무료 서버 기준 목표)
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
}

const BASE = __ENV.BASE || 'https://vocamaster-app.duckdns.org'

export default function () {
  const res = http.get(`${BASE}/public/decks?sort=popular`)
  check(res, { 'status 200': (r) => r.status === 200 })
  sleep(0.3)
}
