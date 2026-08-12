# Redis 컨벤션 (Phase 5)

> Redis에 뭘 넣을지보다 **뭘 넣지 않을지**가 중요하다.
> 원칙: **Redis가 죽어도 서비스는 살아야 한다.** 여기 있는 데이터는 전부 *사라져도 재생성 가능*하거나, *사라지면 기능 하나가 잠깐 느슨해질 뿐*이어야 한다.
> 영구 보존이 필요한 데이터는 MySQL이 주인이다.

## 키 이름 규칙

```
{도메인}:{용도}:{식별자}[:{보조 식별자}]
```

- 구분자는 콜론(`:`) — redis-cli의 `SCAN` 패턴 매칭과 관리 도구 트리 뷰의 사실상 표준
- 소문자 + 하이픈 없음. 식별자는 숫자 id 또는 정규화된 문자열
- 사람이 읽을 수 있어야 한다 (`StringRedisSerializer` 고정 — 직렬화된 바이트 키 금지)

| 키 | 자료구조 | TTL | 용도 | 사라지면? |
|---|---|---|---|---|
| `login:fail:{email}` | String (카운터) | 5분 | 로그인 연속 실패 횟수 | 잠금이 풀림 — 보안이 느슨해질 뿐 서비스는 정상 |
| `login:lock:{email}` | String (플래그) | 30분 | 잠금 상태 | 위와 동일 |
| `popular:decks:{yyyyMMdd}` | Sorted Set | 7일 | 인기 덱 랭킹 | DB `ORDER BY`로 fallback |
| `review:summary:{userId}:{yyyyMMdd}` | String (JSON) | 5분 | 오늘 복습 요약 | 집계 쿼리 재실행 |

> 실제 키는 각 기능 구현 시 이 표에 추가한다. **표에 없는 키를 코드에서 만들지 않는다** — 유령 키가 쌓이면 운영에서 무엇을 지워도 되는지 아무도 모르게 된다.

## TTL 정책

- **모든 키에 TTL을 건다.** TTL 없는 키는 리뷰에서 근거를 요구한다 (메모리는 유한하고, Redis는 가득 차면 무엇을 버릴지 우리 대신 결정한다)
- TTL은 "이 데이터가 틀려도 참을 수 있는 최대 시간"으로 정한다
  - 복습 요약 5분 = "5분 전 숫자를 봐도 사용자가 손해 보지 않는다"
  - 실패 카운터 5분 = "5분 안에 5회"라는 정책 자체가 TTL로 표현됨
- 카운터에 TTL을 걸 때는 **첫 증가에서만** 건다. 매번 갱신하면 창(window)이 계속 밀려 영원히 안 풀린다

## 장애 시 동작 (fail-open)

Redis 연결 실패는 **예외를 밖으로 던지지 않는다.** 사용처마다 정해둔 기본 동작으로 넘어가고 경고 로그를 남긴다.

| 사용처 | Redis 다운 시 |
|---|---|
| rate limit | 제한 없이 통과 (가용성 우선 — 로그인 자체가 막히면 안 됨) |
| 인기 랭킹 | DB `ORDER BY` 경로 |
| 복습 요약 | 집계 쿼리 직접 실행 |

트레이드오프: rate limit의 fail-open은 "Redis를 죽이면 무차별 대입 방어가 풀린다"는 뜻이다. 인증 서버 자체를 지키는 게 우선이라 이 선택을 하되, Phase 7 관측에서 **Redis 다운 알림**으로 보완한다.

## 개발 환경

```bash
docker compose up -d      # redis:7.2-alpine, localhost:6379
docker compose down
```

`redis-cli` 확인 예:

```bash
docker exec -it vocamaster-redis redis-cli
> KEYS login:*          # 개발에서만. 운영은 SCAN (KEYS는 전체를 훑어 서버를 멈춘다)
> TTL login:fail:a@b.com
```
