# VocaMaster

> 망각곡선 기반 반복 학습으로 단어를 장기 기억에 안착시키는 무료 단어장 학습 서비스

영어 · 일본어 단어를 등록(한 장씩 또는 일괄 붙여넣기)해 **플래시카드 · 4지선다 퀴즈 · 타이핑**으로 학습하고,
**Leitner Box 복습 알고리즘**이 "모르는 단어일수록 자주, 아는 단어일수록 가끔" 다시 보여줍니다.
발음 듣기(🔊 브라우저 TTS) · 일본어 요미가나 · 공개 단어장 검색/복사/좋아요 · 학습 통계까지 — **React SPA 11화면**으로 매일 실사용 중입니다.

> **상태:** 🔵 개발 중 — **Phase 6까지 완료 (7/8)** · **시작:** 2026-05 · **목표 마감:** 2027-01
> 진행도와 의사결정 기록: [`docs/CHECKLIST.md`](docs/CHECKLIST.md) · [`docs/decisions.md`](docs/decisions.md) (**ADR 40**)

---

## 🎯 만드는 이유

기존 단어 학습 서비스(Quizlet 등)의 유료화로 생긴 진입장벽을 해결합니다.
"내가 직접 만들어 무료로 쓰고, 같은 처지의 학습자에게 공유한다"가 출발점입니다.

기술적으로는 **혼자서도 운영·설명·수리할 수 있는 백엔드**를 목표로, 모든 설계 결정을
ADR로 남기고(40건), 전수 감사로 찾은 결함을 재현 테스트와 함께 수리하며 진행합니다.

---

## ✅ 구현된 기능 (Phase 0~6)

| 영역 | 기능 |
|---|---|
| 인증 | JWT Access + **Refresh Rotation / Reuse Detection**(재사용 감지 시 전체 세션 무효화) · 로그인 Rate Limit(Redis, 5회/5분 → 30분 잠금) · 회원 관리 |
| 단어장/카드 | CRUD · 소유권 검증 · 검색/정렬 · **읽기(요미가나) 필드** · 별표 · 삭제 시 학습 이력 동반 정리(CASCADE 정책) |
| 일괄 등록 | 붙여넣기 → 구분자 자동 감지 → **미리보기(실패 줄 표시) → 등록** · `단어 \| 읽기 \| 뜻` 3칸 지원 · 중복 skip · 전체 원자성 |
| 학습 3모드 | 플래시카드(안다/모른다) · 4지선다 퀴즈 세션(서버 채점 · 정답 마스킹 · **이번 오답만 재시험**) · 타이핑(쉼표 복수 정답) |
| **복습 (핵심)** | **Leitner Box 6단계** — 답변마다 승급/리셋, due 카드 조회, 오늘 현황판(남은 복습·활동량·연속 학습일) |
| 공개 단어장 | PRIVATE/PUBLIC/UNLISTED · 검색 · **복사(원자적 카운터)** · 좋아요(unique 멱등) · 인기 정렬(like×5+copy×3+study×1) · 존재 숨김 404 |
| Redis | 인기 랭킹 ZSET 캐시 · 로그인 Rate Limit · 오늘 요약 캐시 — **전부 fail-open** (Redis가 죽어도 DB 경로로 동작) |
| 이벤트 | `StudyRecordedEvent` 발행 → 캐시 무효화(@Async) · 인기 점수 study 항(원본 귀속 · 하루 1회 · 자기 학습 제외) 구독 |
| 프론트 | **React 19 + TypeScript SPA 11화면** (홈·덱·학습·퀴즈·타이핑·탐색·공개 상세·통계·설정·가져오기·로그인) · 🔊 브라우저 TTS · 401 자동 토큰 갱신(single-flight) · jar에 번들 |
| 통계 | 최근 28일 활동 · 연속/최고 연속 · 라이트너 분포 · 덱별 진행률(GROUP BY 집계) |
| 오류 계약 | 400/401/403/404/**409**(동시성 충돌)/429(Rate Limit) 통일 JSON 응답 |

## 🔍 기술 하이라이트

- **동시성 테스트가 실제 데드락 3종을 잡음** — ① 복사 API의 FK S락 → X락 승급 교착(잠금 획득 순서로 수리, ADR-031)
  ② 좋아요 더블탭 unique 레이스(DB 제약이 최종 수문장, ADR-032) ③ 출석부 "0행 매치 UPDATE → INSERT"의 **InnoDB 갭 락 교착**
  (upsert 한 방으로 수리, ADR-038). 답 제출은 세션 행 `PESSIMISTIC_WRITE`로 직렬화
- **캐시는 순서만, 판단은 DB** — 인기 랭킹 ZSET에는 id/순서만 캐싱하고 내용·공개 여부는 DB가 재검증
  (비공개 노출 구조적 차단). 무효화는 **커밋 확정 후**(`afterCommit`/`@TransactionalEventListener`) —
  커밋 전 빈틈에 낡은 값이 재캐싱되는 레이스 차단 (ADR-035~037)
- **비동기는 "복구 가능한 것만"** — 캐시 삭제는 `@Async`(유실돼도 TTL이 상한), 출석부·카운터는 동기
  (원본 기록은 유실 시 복구 불가). 큐 포화는 CallerRuns로 유실 0 (ADR-039)
- **존재 숨김(404) 보안 계약** — 남의 비공개 덱은 "없는 덱"과 상태·코드·메시지까지 동일 응답 (열거 공격 차단),
  HTTP 레벨 테스트로 박제 (ADR-030)
- **테스트 160** — Testcontainers **실제 MySQL 8** + Flyway(V1~V15) 검증, H2 미사용.
  트랜잭션 경계·동시성이 관심사인 테스트는 자동 롤백을 끄고 운영과 동일한 커밋 경계로 검증
- **정직한 감사 문화** — 전수 감사([7월](docs/audit-2026-07.md) · 8월 ADR-040)로 찾은 결함을
  재현 테스트와 함께 수리하고, 남긴 것은 트레이드오프로 문서화

## 📋 로드맵

| Phase | 주제 | 상태 |
|---|---|---|
| 0 | 부트스트랩 (설정 분리 · Flyway · 예외 통일) | ✅ 2026-05 |
| 1 | Refresh Rotation · Reuse Detection · 회원 관리 | ✅ 2026-05 |
| 2 | 검색/정렬 · 일괄 등록 · 퀴즈 세션 · 타이핑 · 오답노트 | ✅ 2026-06 |
| 3 | **Leitner Box 반복 학습** · 연속 학습일 · 동시성 | ✅ 2026-07 |
| 4 | 공개 단어장 검색 · 복사 · 좋아요 · 인기 정렬 | ✅ 2026-08 |
| 5 | Redis (Rate Limit · 랭킹 캐시 · 요약 캐시 · fail-open) | ✅ 2026-08 |
| 6 | 비동기 이벤트 (Spring Event · AFTER_COMMIT · @Async) + **React SPA 11화면** | ✅ 2026-08 |
| 7 | Docker · CI/CD · 배포 · k6 부하 테스트 (Redis 전후 측정) | 🔵 다음 |
| 8 | 마감 · 문서/면접 준비 | 예정 |

> Kafka는 검토 후 **도입하지 않기로 결정** — 단일 앱에서 브로커는 과한 도구, Spring Event로 경계를 긋고
> "두 번째 앱·유실 없는 비동기·재생"이 실제로 필요해질 때 재평가 (ADR-037 대안 분석)

---

## 🛠 기술 스택

**Backend** Java 17 · Spring Boot 3.3 · Spring Security · Spring Data JPA · Validation
**Frontend** React 19 · TypeScript · Vite (Gradle이 빌드해 jar에 번들 — 별도 배포 없음)
**Auth** JWT (jjwt) — Access(단기) + Refresh(14일, rotation)
**Database** MySQL 8 · Flyway (V1~V15) · **Redis 7** (Lettuce — 랭킹·Rate Limit·캐시, fail-open)
**Test** JUnit 5 · **Testcontainers (MySQL 8 + Redis)** — H2 미사용, 운영과 동일 DB로 검증
**Docs** springdoc-openapi (Swagger)

---

## ▶️ 실행 방법

### 사전 준비

- JDK 17 (Temurin 권장) · Node.js 20+ (React 빌드 — Gradle이 자동 실행)
- MySQL 8 — 로컬에 `vocamaster` 데이터베이스 생성
- (선택) **Docker Desktop** — Redis 컨테이너(`docker compose up -d`)와 테스트용 Testcontainers.
  Redis 없이도 동작합니다 (fail-open — 랭킹·Rate Limit·캐시만 DB 경로로 후퇴)

### 실행

```bash
# Windows — 원클릭 (Redis + React 빌드 + jar 빌드 + 서버 + 브라우저)
start-vocamaster.bat

# 또는 직접
gradlew.bat bootRun        # Windows
./gradlew bootRun          # macOS / Linux — React 번들은 Gradle이 자동 빌드
```

기본 프로필(dev)은 `localhost:3306/vocamaster`(root)로 접속합니다 — 필요 시
`src/main/resources/application-dev.yml`을 수정하세요. 운영(prod) 프로필은
`DB_URL` / `DB_USERNAME` / `DB_PASSWORD` / `JWT_SECRET` 환경변수를 요구합니다.

### 접속

| 경로 | 설명 |
|---|---|
| `http://localhost:8080/app/` | **React 앱** (회원가입 → 덱 → 학습/퀴즈/타이핑/통계) |
| `http://localhost:8080/api-docs` | Swagger UI (전체 API 명세) |

### 테스트

```bash
gradlew.bat test   # Docker Desktop 실행 상태에서 — 160 tests
```

---

## 📂 프로젝트 구조

```
src/main/java/com/vocamaster
├── auth/         # 인증 — JWT · Refresh Rotation · Reuse Detection
├── user/         # 회원 관리
├── deck/         # 단어장 · 공개/복사/좋아요 · 인기 랭킹(Redis)
├── card/         # 카드 (검색/정렬/별표/읽기)
├── cardimport/   # 텍스트 일괄 등록 (미리보기 → 등록)
├── study/        # 플래시카드 세션 · 학습 이벤트(StudyRecordedEvent)
├── quiz/         # 4지선다 퀴즈 (세션 기반 · 오답 재시험)
├── typing/       # 타이핑 모드 (쉼표 복수 정답)
├── wrongnote/    # 통합 오답노트 (3모드 합산)
├── review/       # ★ Leitner Box 복습 (핵심 도메인) · 요약 캐시
├── stats/        # 출석부 · 연속 학습일 · 통계 API
├── page/         # (구형) Mustache 페이지 — React로 대체 중
├── common/       # 예외 계약 · 공통 유틸
└── config/       # Security / Redis / Async / Swagger

frontend/         # React 19 + TS + Vite — 빌드 산출물은 jar 안 static/app/
└── src/pages/    # 11화면 (홈·덱·학습·퀴즈·타이핑·탐색·통계·설정·가져오기…)
```

---

## 📚 문서

| 문서 | 내용 |
|---|---|
| [`docs/CHECKLIST.md`](docs/CHECKLIST.md) | Phase 0~8 상세 체크리스트 (진행의 단일 원장) |
| [`docs/decisions.md`](docs/decisions.md) | **ADR 40** — 모든 설계 결정의 대안·근거·트레이드오프 |
| [`docs/review-algorithm.md`](docs/review-algorithm.md) | Leitner Box 알고리즘 — 규칙 · 왜 SM-2/FSRS가 아닌가 |
| [`docs/cache-strategy.md`](docs/cache-strategy.md) | 캐시 전략 — cache-aside · 무효화 타이밍 · fail-open |
| [`docs/redis-conventions.md`](docs/redis-conventions.md) | Redis 키 규칙 · 직렬화 함정 |
| [`docs/audit-2026-07.md`](docs/audit-2026-07.md) | 전수 감사 결과 분류와 수리 추적 |
| [`docs/auth-design.md`](docs/auth-design.md) | 인증 설계 (토큰 흐름 · 쿠키 정책) |
| [`docs/notes/`](docs/notes/) | 주차별 학습 노트 (면접 Q&A 원고 포함) |

---

## 📝 라이선스

학습/포트폴리오 목적의 개인 프로젝트입니다.
