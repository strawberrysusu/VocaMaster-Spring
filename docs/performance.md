# 성능 실측 — Redis 랭킹 전/후 (Phase 7 ⑧)

> 2026-08-27 밤, 운영 서버 실측. 부하 도구·시나리오는 [`tools/k6/popular.js`](../tools/k6/popular.js).

## 측정 대상

**공개 덱 인기 정렬** `GET /public/decks?sort=popular` — Phase 5의 주인공.
같은 엔드포인트를 두 경로로 측정:

- **Redis ON**: ZSET 랭킹 조회 (`like×5 + copy×3 + study×1` 점수, ADR-034·038)
- **Redis OFF** (`ranking.popular.enabled=false`): Phase 5 이전의 DB 경로 — 조인 + 정렬(filesort)

## 환경 (숫자를 읽을 때의 전제)

- 서버: Oracle Cloud Tokyo, **무료 1GB Micro 2대 분산** (app / MySQL+Redis) — 저사양 실측
- 경로: **한국 → 도쿄 실사용 경로** (HTTPS, nginx 경유) — 왕복 ~35ms가 모든 수치에 포함
- 부하: VU 10, 워밍업 15초(JIT·커넥션 풀 예열, 수치 제외) + 측정 45초, 요청 간 0.3s
- 실패율: 양쪽 모두 0%

## 결과

| 지표 | Redis ON (ZSET) | Redis OFF (DB) | 차이 |
|---|---|---|---|
| 평균 | 66.0ms | 126.0ms | 1.9× |
| p50 | 52.6ms | 73.9ms | 1.4× |
| p90 | 105.6ms | 287.4ms | 2.7× |
| **p95** | **112.6ms** | **399.5ms** | **3.5×** |
| **p99** | **175.2ms** | **698.5ms** | **4.0×** |
| max | 212.6ms | 900.0ms | 4.2× |
| 처리 요청 | 1,434 | 1,231 | — |

## 해석

- **개선은 꼬리에서 온다.** 중앙값(p50)은 1.4×지만 p95는 3.5×, p99는 4.0× — DB 경로의
  filesort는 부하가 겹칠 때 지연이 크게 출렁이고(최대 900ms), ZSET은 이미 정렬된 자료구조를
  읽기만 하므로 부하에서도 평평하다. **캐시·자료구조 도입의 가치는 평균이 아니라 p95/p99로 말해야 한다.**
- 저사양(1GB)일수록 격차가 벌어진다 — 정렬 연산이 CPU 1/8코어를 직접 두드리기 때문.
  같은 코드가 큰 서버에서는 덜 극적이었을 것.
- 왕복 ~35ms가 바닥에 깔려 있으므로 서버 내부 처리만 보면 격차는 표보다 더 크다.

## 한계 (정직하게)

- 데이터 규모가 작다 (덱 수십 개) — 수만 덱이면 DB 경로는 더 나빠지고 ZSET은 O(log N)으로 완만할 것이나, 실측은 아님
- 단일 엔드포인트, 읽기 전용 시나리오 — 쓰기 경합·복습 요약 캐시는 미측정 (후속 후보)
- VU 10의 가벼운 부하 — 무료 서버 보호를 위한 의도적 상한

## 재현

```bash
k6 run tools/k6/popular.js                       # Redis ON (운영 기본)
# 서버에서: docker compose -f docker-compose.app.yml -f ranking-off.yml up -d  (토글 off 재기동)
k6 run tools/k6/popular.js                       # Redis OFF
# 원복: docker compose -f docker-compose.app.yml up -d
```
