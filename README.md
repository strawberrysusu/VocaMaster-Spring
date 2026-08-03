# VocaMaster

> 망각곡선 기반 반복 학습으로 단어를 장기 기억에 안착시키는 무료 단어장 학습 서비스

영어 · 일본어 단어를 직접 등록해 플래시카드 · 5지선다 퀴즈 · 타이핑 모드로 학습하고,
**Leitner Box 복습 알고리즘**이 "모르는 단어일수록 자주, 아는 단어일수록 가끔" 다시 보여줍니다.

> **상태:** 🔵 개발 중 — **Phase 3까지 완료 (4/8)** · **시작:** 2026-05 · **목표 마감:** 2027-01
> 진행도와 의사결정 기록: [`docs/CHECKLIST.md`](docs/CHECKLIST.md) · [`docs/decisions.md`](docs/decisions.md) (ADR 29+)

---

## 🎯 만드는 이유

기존 단어 학습 서비스(Quizlet 등)의 유료화로 생긴 진입장벽을 해결합니다.
"내가 직접 만들어 무료로 쓰고, 같은 처지의 학습자에게 공유한다"가 출발점입니다.

기술적으로는 **혼자서도 운영·설명·수리할 수 있는 백엔드**를 목표로, 모든 설계 결정을
ADR로 남기고(29건+), 전수 감사로 찾은 결함을 재현 테스트와 함께 수리하며 진행합니다.

---

## ✅ 구현된 기능 (Phase 0~3)

| 영역 | 기능 |
|---|---|
| 인증 | JWT Access + **Refresh Rotation / Reuse Detection**(재사용 감지 시 전체 세션 무효화) · httpOnly 쿠키 · 회원 관리(닉네임/비밀번호/탈퇴) |
| 단어장/카드 | CRUD · 소유권 검증 · 검색/정렬(별표·위치·생성일) · 페이지네이션 |
| 일괄 등록 | 구분자 자동 감지 · 중복 skip · 1000줄 상한 · 전체 원자성(중간 실패 시 전량 취소) |
| 학습 3모드 | 플래시카드(안다/모른다) · 5지선다 퀴즈 세션(서버 채점·정답 마스킹) · 타이핑(복수 정답 허용) |
| 오답노트 | 3개 모드 오답 통합 조회 (중복 제거) |
| **복습 (핵심)** | **Leitner Box 6단계** — 답변마다 박스 승급/리셋, due 카드 조회, 오늘 현황판(남은 복습/오늘 복습/활동량/연속 학습일) |
| 출석/통계 | 모든 학습 모드가 출석부(`daily_user_stats`)에 집계 — 연속 학습일(streak) 계산 |
| 오류 계약 | 400/401/403/404/**409**(동시성 충돌) 통일 JSON 응답 |

## 🔍 기술 하이라이트

- **동시성 처리 3종** — 데이터 성격에 따라 다른 도구:
  카드 진행 상태는 `@Version` 낙관적 락(충돌 시 409), 학습 카운터는 원자적 UPDATE(증가 분실 방지),
  출석부 최초 생성 경쟁은 MySQL upsert(`ON DUPLICATE KEY UPDATE`)로 흡수
- **보안 트랜잭션 경계 설계** — Refresh 재사용 감지 시의 전체 세션 무효화가 401 롤백에
  증발하던 결함을 재현 테스트(실제 커밋 경계)로 실증 후, "감지 트랜잭션 종료(락 해제) →
  별도 트랜잭션에서 제재 커밋 → 401" 구조로 수리 ([커밋 d49dc62](../../commit/d49dc62))
- **테스트** — Testcontainers **실제 MySQL 8** + Flyway 마이그레이션 검증, 70+ 테스트.
  트랜잭션 경계가 관심사인 테스트는 자동 롤백을 끄고 운영과 동일한 커밋 경계로 검증
- **정직한 감사 문화** — 전수 코드 감사 결과를 [`docs/audit-2026-07.md`](docs/audit-2026-07.md)로
  분류·추적 (수리 완료/예정/백로그)

## 📋 로드맵

| Phase | 주제 | 상태 |
|---|---|---|
| 0 | 부트스트랩 (설정 분리 · Flyway · 예외 통일) | ✅ |
| 1 | Refresh Rotation · Reuse Detection · 회원 관리 | ✅ |
| 2 | 검색/정렬 · 일괄 등록 · 퀴즈 세션 · 타이핑 · 오답노트 | ✅ |
| 3 | **Leitner Box 반복 학습** · 연속 학습일 · 동시성 | ✅ 2026-08 |
| 4 | 공개 단어장 검색 · 복사 · 좋아요 | 🔵 다음 |
| 5 | Redis (인기 랭킹 · Rate Limit · 캐시) | 예정 |
| 6 | 비동기 이벤트 (Spring Event → Kafka 검토) | 예정 |
| 7 | Docker · CI/CD · k6 부하 테스트 | 예정 |
| 8 | 마감 · 문서/면접 준비 | 예정 |

---

## 🛠 기술 스택

**Backend** Java 17 · Spring Boot 3.3 · Spring Security · Spring Data JPA · Validation
**Auth** JWT (jjwt) — Access(단기) + Refresh(14일, rotation)
**Database** MySQL 8 · Flyway (V1~V8)
**Test** JUnit 5 · **Testcontainers (MySQL 8)** — H2 미사용, 운영과 동일 DB로 검증
**View** Mustache (데모 UI) · **Docs** springdoc-openapi (Swagger)

---

## ▶️ 실행 방법

### 사전 준비

- JDK 17 (Temurin 권장)
- MySQL 8 — 로컬에 `vocamaster` 데이터베이스 생성
- (테스트 실행 시) **Docker Desktop** — Testcontainers가 MySQL 컨테이너를 띄웁니다

### 실행

```bash
# Windows
gradlew.bat bootRun

# macOS / Linux
./gradlew bootRun
```

기본 프로필(dev)은 `localhost:3306/vocamaster`(root)로 접속합니다 — 필요 시
`src/main/resources/application-dev.yml`을 수정하세요. 운영(prod) 프로필은
`DB_URL` / `DB_USERNAME` / `DB_PASSWORD` / `JWT_SECRET` 환경변수를 요구합니다.

### 접속

| 경로 | 설명 |
|---|---|
| `http://localhost:8080/pages/login` | Mustache 데모 (회원가입 → 덱 → 학습) |
| `http://localhost:8080/api-docs` | Swagger UI (전체 API 명세) |

### 테스트

```bash
gradlew.bat test   # Docker Desktop 실행 상태에서
```

---

## 📂 프로젝트 구조

```
src/main/java/com/vocamaster
├── auth/         # 인증 — JWT · Refresh Rotation · Reuse Detection
├── user/         # 회원 관리
├── deck/         # 단어장
├── card/         # 카드 (검색/정렬/별표)
├── cardimport/   # 텍스트 일괄 등록
├── study/        # 플래시카드 학습 세션
├── quiz/         # 5지선다 퀴즈 (세션 기반)
├── typing/       # 타이핑 모드
├── wrongnote/    # 통합 오답노트
├── review/       # ★ Leitner Box 복습 (핵심 도메인)
├── stats/        # 출석부 · 연속 학습일
├── page/         # Mustache 페이지
├── common/       # 예외 계약 · 공통 유틸
└── config/       # Security / Swagger / Jackson
```

---

## 📚 문서

| 문서 | 내용 |
|---|---|
| [`docs/CHECKLIST.md`](docs/CHECKLIST.md) | Phase 0~8 상세 체크리스트 (진행의 단일 원장) |
| [`docs/decisions.md`](docs/decisions.md) | ADR 29+ — 모든 설계 결정의 대안·근거·트레이드오프 |
| [`docs/review-algorithm.md`](docs/review-algorithm.md) | Leitner Box 알고리즘 — 규칙 · 왜 SM-2/FSRS가 아닌가 |
| [`docs/audit-2026-07.md`](docs/audit-2026-07.md) | 전수 감사 결과 분류와 수리 추적 |
| [`docs/auth-design.md`](docs/auth-design.md) | 인증 설계 (토큰 흐름 · 쿠키 정책) |
| [`docs/notes/`](docs/notes/) | 주차별 학습 노트 |

---

## 📝 라이선스

학습/포트폴리오 목적의 개인 프로젝트입니다.
