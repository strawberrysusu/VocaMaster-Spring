# VocaMaster 진행 체크리스트

> 시작일: 2026-05-04 · 목표 완료: 2027-01-04 (8개월 / ~34주)
> 마지막 업데이트: 2026-05-04

---

## 📍 현재 상태

| 항목 | 값 |
|---|---|
| **진행 중인 Phase** | **Phase 2 진행 중** (Card 검색/정렬 ✅ / Card 필드 ✅ / 일괄등록 + Quiz + Typing 남음) |
| **이번 주 집중** | 일괄 등록 강화 (#3) → Quiz 세션 단위 (#4) |
| **전체 진행도** | Phase 0 ✅ / Phase 1 ✅ / Phase 2 ~35% / Phase 3~8 대기 |
| **다음 마일스톤** | Phase 2 — 일괄 등록 + Quiz 강화 + Typing 모드 |

---

## 📖 사용법

### 체크박스
- `[ ]` 미완료 → 끝나면 즉시 `[x]`로 변경하고 커밋
- **커밋 단위**: 한 체크박스 또는 *논리적으로 묶인 작은 단위* = 한 커밋
  (예: `application-{dev,test,prod}.yml` 분리는 묶어서 한 커밋 OK)

### 모드 (Mode)
어떤 방식으로 만들지를 표시. 섹션 또는 항목 단위.
- 🟢 **A** = 내가 직접 짜고 Claude는 가이드 + 리뷰
- 🔵 **B** = Claude와 한 줄씩 페어 (면접 단골 영역)
- ⚪ **C** = 데모 받고 닫고 다시 짜기 (처음 보는 기술)

### 우선순위 (Priority)
완수 의무도. 항목 단위로 표기.
- **[MUST]** — 이거 없으면 프로젝트 핵심이 약해짐. 무조건 한다.
- **[SHOULD]** — 있으면 좋고, 없어도 프로젝트는 성립.
- **[STRETCH]** — 시간 남으면 보너스. 못 해도 OK.

> Phase는 **MUST 전부 + SHOULD 절반 이상**이면 완료로 봄. STRETCH는 *완전히 무시 가능*.

### 운영 규칙
- **90분 룰**: 한 작업이 90분 넘게 막히면 무조건 도움 요청. 혼자 끙끙 X
- 새 기능 떠오르면 해당 Phase 하단 `### 🆕 추가 아이디어`에 적기. 본 리스트에 즉시 끼워넣지 말 것
- Phase 진행 중에 다른 Phase 항목 손대지 말 것 (한 우물만)

---

## 🤖 AI 사용 규칙 (2026-05-08 확정)

> 이 체크리스트의 목표는 체크박스를 다 채우는 게 아니라, 8개월 뒤 핵심 코드를 *내가 설명하고 고칠 수 있게* 되는 것.

### 🟢 운영 모드 (필수 준수)

**핵심 코드 (Service/Controller/Entity/테스트)는 사용자가 직접 타이핑한다.**

- Claude는 **어디에 / 무엇을 / 어떻게**의 3가지로 안내만 (Write/Edit 자제)
- 사용자가 *손으로* 코드 친 후 막힐 때만 Claude가 직접 수정
- 패턴이 헷갈리면 Claude가 *예시 한 블록* 보여줌 → 사용자가 *손으로* 옮겨치며 이해

**이유:** "AI가 다 짜고 사용자는 읽기만"하면 NewsPick 반복 — 면접에서 무너짐. 손가락이 한 번 친 후에 이해가 더 깊어짐.

### ⚪ Claude가 처리해도 OK인 영역

- yml / build.gradle / .env.example 같은 보일러플레이트 설정
- Flyway 마이그레이션 SQL (가이드는 함께)
- 단순 import 정리, 변수명 변경
- git 명령, CHECKLIST 갱신

### 적극 사용해도 되는 영역

- 설계 대안 제시, 트레이드오프 정리
- API/테이블/테스트 케이스 목록 뽑기
- 에러 원인 분석, 코드 리뷰
- README/문서 다듬기, 면접 질문 만들기

### 금지 사항

- ❌ AI가 짠 코드를 *읽지 않고* 바로 커밋
- ❌ 에러 메시지만 던지고 수정 코드 그대로 복붙
- ❌ "왜 되는지 모르는데" 다음 단계로 넘어가기
- ❌ "이 기능 전체 만들어줘" 같은 통째 요청
- ❌ Claude가 핵심 코드를 Write/Edit로 박고 사용자가 "옮겨치기"만 하는 패턴

### 알려진 함정

- **`application.yml`(main)에 새 키 추가 시 → `src/test/resources/application.yml`에도 *반드시* 같은 키 추가.** 두 파일이 따로 관리됨. 안 하면 PropertyPlaceholderHelper IllegalArgumentException 발생 (이미 두 번 당함)

### 📜 ADR (Architecture Decision Record) 정책

- 새 기능/기술 도입 *전*에 `docs/decisions.md`에 ADR 추가 (또는 `docs/decisions/ADR-NNN-제목.md` 분리)
- Claude는 *코드 안내 전에* 항상 "왜 이걸로 가는가 / 대안은" 제시 후 사용자 결정 받기
- 양식: 상태 / 범위 / 컨텍스트 / 대안 3개+ / 결정 / 근거 / 트레이드오프
- 5~10개 누적되면 디렉토리 분리 (한 파일 → 개별 파일)
- 현재 ADR 15개 누적 — `docs/decisions.md` 참조

**핵심 기능(B 모드) 작업 루틴**
1. 내가 요구사항 5줄 작성
2. 내가 API 경로 / Request·Response 예시 / 테스트 케이스 목록 작성
3. AI에게 "코드 주지 말고 설계만" 요청
4. 내가 Entity → Repository → Service → Controller 직접 구현
5. 막히면 질문 (90분 룰)
6. 완성 후 AI 리뷰 ("문제점만 먼저, 수정 코드는 그다음")
7. 핵심 기능은 최소 1개 테스트를 *내가 직접* 작성

---

## 🎤 설명 가능 기준

핵심 기능은 아래 5개를 만족해야 진짜 "완료"로 본다.

- [ ] 왜 만들었는지 (요구사항/동기) 설명 가능
- [ ] 요청 → Controller → Service → Repository → DB 흐름을 그림으로 설명 가능
- [ ] 주요 예외 케이스 3개 이상 설명 가능
- [ ] 테스트 케이스 2개 이상 직접 설명 가능
- [ ] 개선 전/후 또는 트레이드오프 설명 가능 (왜 X 안 쓰고 Y 썼는지)

> 4개 이상 만족 → 내 코드. 2개 이하 → 아직 AI 코드.

---

## 📆 매주 완료 기준

매 주말에 자가 점검.

- [ ] 이번 주 체크박스 최소 2개 완료
- [ ] 테스트 깨진 상태로 주말 넘기지 않기
- [ ] `docs/notes/week-N.md` 학습 노트 작성
- [ ] 새로 생긴 아이디어는 "추가 아이디어"에만 기록 (본 리스트 침범 X)
- [ ] 다음 주 작업 우선순위 재정렬

---

## 📅 일정 / 버퍼 (목표 ~34주)

| 구간 | 기간 | 누적 |
|---|---|---|
| Phase 0 — 부트스트랩 | 1~2주 | 2주 |
| Phase 1 — 인증 강화 | 4주 | 6주 |
| Phase 2 — CRUD + 학습 모드 | 4주 | 10주 |
| **버퍼** | **1주** | **11주** |
| Phase 3 — 반복 학습 알고리즘 | 4주 | 15주 |
| Phase 4 — 공개 단어장 / 공유 | 4주 | 19주 |
| **버퍼** | **1주** | **20주** |
| Phase 5 — Redis | 3주 | 23주 |
| Phase 6 — 비동기 이벤트 | 3주 | 26주 |
| Phase 7 — 배포 / 성능 / 관측 | 4주 | 30주 |
| Phase 8 — 마감 / 면접 준비 | 3주 | 33주 |

> 시험/과제/번아웃/배포 사고 등 예상 못한 일은 *반드시* 생김. 버퍼 주차에 미뤄둔 작업 처리 또는 휴식.
> 30주에 끝나면 Phase 8을 늘려서 더 깔끔하게 마감.

---

## ✅ MVP 베이스 (이미 완료, 2026-05 이전 작업)

<details>
<summary><b>인증/회원</b> — 4 items</summary>

- [x] 회원가입 API (`POST /api/auth/register`)
- [x] 로그인 API (`POST /api/auth/login`) — JWT 단일 토큰 7일
- [x] JWT 인증 필터 (`JwtAuthFilter`)
- [x] Spring Security 기본 설정 (`SecurityConfig`)

</details>

<details>
<summary><b>Deck / Card</b> — 6 items</summary>

- [x] Deck 엔티티 (User 1:N, Card 1:N)
- [x] Deck CRUD + 소유권 검증 (`DeckService.verifyOwner`)
- [x] Card 엔티티 (front/back/starred)
- [x] Card CRUD + 별표 토글
- [x] Card 페이지네이션 조회
- [x] Card 텍스트 일괄 등록 (`ImportService`) + preview

</details>

<details>
<summary><b>Quiz / Study</b> — 6 items</summary>

- [x] 5지선다 퀴즈 생성 (`QuizService.generate`)
- [x] 서버 측 정답 검증 + `QuizAttempt` 저장
- [x] 퀴즈 이력 조회
- [x] 학습 세션 시작 (`StudyService`)
- [x] 안다/모른다 기록 (`StudyRecord`)
- [x] 덱별 통계 API

</details>

<details>
<summary><b>인프라/UX</b> — 5 items</summary>

- [x] Mustache UI 데모 (회원가입/로그인/덱/카드/퀴즈/학습)
- [x] 한글 인코딩 설정 (UTF-8 force)
- [x] Swagger UI (`/api-docs`)
- [x] `GlobalExceptionHandler` 기본형
- [x] 서비스 테스트 (auth, card, import, quiz, study) — H2 기반

</details>

---

## 🟢 Phase 0 — 부트스트랩 (1~2주)

> **목표:** "내가 이해할 수 있는 안정적인 백엔드 뼈대"로 재정비.
> 새 기능 X. 기존 코드 정돈 + 운영 가능한 형태로 변환.
> **모드:** 🟢 A (전부 직접)

### ⚠️ Flyway 안전 규칙 (작업 전 필독)
- [x] **[MUST]** 한 번 적용한 `V*.sql`은 **절대 수정 금지** (checksum mismatch 발생)
- [x] **[MUST]** 변경이 필요하면 새 `V{n+1}__변경.sql` 파일 추가
- [x] **[MUST]** 로컬 DB는 마이그레이션 시작 전 drop 후 재생성
- [ ] **[SHOULD]** `docs/migration-rule.md` 작성 (이 규칙 + 실수 사례)

### 📝 문서
- [x] **[MUST]** `README.md` — 서비스 소개 / 기술 스택 / 실행 방법 / 진행 상태 *(초안 작성 완료, Phase 진행하며 갱신)*
- [ ] **[SHOULD]** `docs/ROADMAP.md` — 8개월 로드맵 요약 (의도/이유 위주)
- [ ] **[SHOULD]** `docs/ERD.md` — dbdiagram.io 코드 + 캡처 이미지
- [ ] **[SHOULD]** `docs/notes/week-1.md` — 학습 노트 첫 주 시작

### ⚙️ 설정 분리 + 환경변수화
- [x] **[MUST]** `application.yml` 공통 설정만 남기기
- [x] **[MUST]** `application-dev.yml` 분리 (로컬 MySQL, show-sql=true)
- [x] **[MUST]** `application-test.yml` 분리 (H2, ddl-auto=create-drop)
- [x] **[MUST]** `application-prod.yml` 분리 (env vars only, ddl-auto=validate, show-sql=false)
- [x] **[MUST]** DB url/username/password → `${DB_URL}` 등으로 추출 (prod만, dev는 yml에 직접)
- [x] **[MUST]** JWT secret → `${JWT_SECRET}` 추출 (prod만, dev는 yml에 직접)
- [x] **[MUST]** `.env.example` 작성 (실제 `.env`는 이미 `.gitignore`에 등록됨)
- [ ] **[SHOULD]** README에 "로컬 실행 시 환경변수 설정 방법" 보강 (prod 배포 시점에)

### 🗄️ Flyway 도입
- [x] **[MUST]** `build.gradle`에 `flyway-core` + `flyway-mysql` 추가
- [x] **[MUST]** `src/main/resources/db/migration/` 디렉토리 생성
- [x] **[MUST]** `V1__init_schema.sql` — 현재 엔티티 직접 SQL로 작성
- [x] **[MUST]** `application.yml`에 Flyway 설정 추가 (dev: enabled, test: disabled)
- [x] **[MUST]** dev profile에서 `ddl-auto: validate`로 변경
- [x] **[MUST]** 로컬에서 마이그레이션 한 번 돌려서 검증 (DB drop → recreate → bootRun → V1 적용 + JPA validate 통과)
- [x] **[SHOULD]** test profile은 `ddl-auto=create-drop` 유지 (Flyway off)
- [x] **[보너스]** 6개 테스트 클래스에 `@ActiveProfiles("test")` 명시 (default profile=dev로 떨어져 H2에 MySQL용 V1이 실행되던 문제 해결)

### 🧹 코드 정리
- [ ] **[SHOULD]** `BaseTimeEntity` 추가 (`createdAt`, `updatedAt`) — 모든 엔티티 상속 (다음 Phase에서)
- [x] **[MUST]** `ErrorResponse` DTO 통일 형식 정의 (status/code/message/timestamp)
- [x] **[MUST]** `GlobalExceptionHandler` 보강 — `NotFoundException`, `ForbiddenException`, `BadRequestException`, `UnauthorizedException` 분리 + 보안 일관성 (이메일 없음/비번 틀림 모두 401)
- [x] **[MUST]** 서비스 코드의 `ResponseStatusException`, `IllegalArgumentException` 사용처 정리 (11건 교체 + legacy 핸들러 제거 + 9개 테스트 assertThrows 갱신)
- [ ] **[STRETCH]** 패키지 구조 점검

### 📓 학습 노트
- [x] **[SHOULD]** `docs/notes/week-1.md` 작성 (한 일 / 이해한 것 / 헷갈리는 것 / 면접 질문 3개)

### ✅ Phase 0 완료 기준
- [x] 모든 MUST 항목 완료
- [x] `gradlew bootRun`으로 dev profile 정상 실행
- [x] `gradlew test` 그린 (24/24)
- [x] `application-prod.yml`에 비밀 정보 0개 (전부 env var)
- [x] Flyway로 빈 DB → 현재 스키마 재현 성공
- [x] README에 환경변수 설정 안내 반영

### 🆕 추가 아이디어
*(공란)*

---

## 🔵 Phase 1 — 인증 강화 (4주)

> **목표:** 신입 백엔드 면접에서 통할 인증 구조.
> NewsPick의 refresh token rotation 구조를 *이해하면서 다시* 구현.
> **모드:** 🔵 B (refresh token / reuse detection) + 🟢 A (회원 관리)

### 🔐 Refresh Token Rotation 🔵
- [x] **[MUST]** `docs/auth-design.md` — Access/Refresh 흐름 설계 문서 *먼저* 작성
- [x] **[MUST]** `refresh_tokens` 테이블 설계 (id, user_id, **token_hash**, expires_at, revoked_at, created_at + forensics: user_agent, last_used_ip)
- [x] **[MUST]** `V2__add_refresh_tokens.sql` 작성
- [x] **[MUST]** `refresh_tokens.token_hash` unique index
- [x] **[MUST]** `refresh_tokens.user_id` index (FK 부수효과로 자동 생성)
- [x] **[MUST]** `RefreshToken` 엔티티 + Repository (atomic UPDATE `revokeIfActive`, mass logout `revokeAllByUserId`)
- [x] **[MUST]** Refresh token raw 값은 DB 저장 X — **SHA-256 해시만** 저장 (CHAR(64))
- [x] **[MUST]** Access token 수명 30분~1시간으로 단축, Refresh 14일
- [x] **[MUST]** JWT claim에 `jti(UUID)` 추가
- [x] **[MUST]** Access는 `type=access`, Refresh는 `type=refresh` claim 추가
- [x] **[MUST]** `POST /api/auth/refresh` 엔드포인트
- [x] **[MUST]** Refresh token rotation 로직 (사용 시 즉시 폐기 + 새 토큰 발급)
- [x] **[MUST]** `POST /api/auth/logout` — refresh token 폐기

### 🛡️ Reuse Detection (면접 차별화 포인트) 🔵
- [x] **[MUST]** 폐기된 토큰 재사용 시 **해당 사용자 모든 세션 무효화** (mass logout)
- [ ] **[SHOULD]** 비밀번호 변경 시 모든 refresh token 폐기 (비번 변경 API 추가 시 처리)
- [x] **[SHOULD]** Refresh token 동시성 처리 — atomic UPDATE (CAS) 적용 + `@Modifying(flushAutomatically, clearAutomatically)`

### 🍪 Refresh Token 전달 방식 결정
- [x] **[MUST]** Refresh token = **httpOnly cookie**로 결정 (XSS 노출 차단)
- [x] **[MUST]** Cookie path `/auth`로 제한 (Controller 매핑 따라 — `/auth/refresh`, `/auth/logout`만 첨부)
- [x] **[MUST]** prod: `Secure=true` + `SameSite=Strict` (`auth.cookie.*` yml 키 + `@Value` 주입으로 환경별 분기)
- [x] **[MUST]** dev: `SameSite=Lax` + `Secure=false`
- [x] **[SHOULD]** Access token은 body로 반환, 클라이언트가 메모리/localStorage 저장

### 👤 회원 관리 보강 🟢
- [x] **[MUST]** `GET /users/me`
- [x] **[MUST]** `PATCH /users/me` — 닉네임 변경
- [x] **[MUST]** `PATCH /users/me/password` — 비밀번호 변경 + mass logout 자동
- [x] **[SHOULD]** `DELETE /users/me` — 회원 탈퇴 (소프트 삭제: `deletedAt` + mass logout + 재로그인 차단)

### 🛡️ 보안/유틸 정리 🔵
- [x] **[MUST]** `CustomUserDetails` 도입 (`CurrentUser.get()` 직접 캐스팅 제거 + JwtAuthFilter DB 조회 제거)
- [x] **[MUST]** `@AuthenticationPrincipal CustomUserDetails` 패턴으로 UserController 정리 (기존 컨트롤러는 `CurrentUser.getId()` 호환)
- [x] **[MUST]** 페이지네이션 안전장치 (`PageableUtils.safe`: `page<0→0`, `size 1~100`)
- [x] **[보너스]** JwtAuthFilter `type=access` 검증 — refresh 토큰으로 일반 API 호출 차단

### 🧹 운영 보조
- [ ] **[STRETCH]** 만료/폐기 refresh token cleanup 스케줄러

### 🧪 테스트
- [x] **[MUST]** AuthService — refresh rotation 성공
- [x] **[MUST]** AuthService — 만료된 refresh 거부 (`@TestPropertySource`로 expiration=1ms override + Thread.sleep — Clock 주입 없이 깔끔)
- [x] **[MUST]** AuthService — **reuse detection** (폐기된 토큰 재사용 시 mass logout)
- [x] **[보너스]** AuthService — access token으로 /refresh 시도 거부 (type 검증)
- [x] **[보너스]** AuthService — logout 후 refresh 사용 불가
- [x] **[SHOULD]** AuthService — 비밀번호 변경 시 기존 refresh 무효화 (UserService.changePassword에서 mass logout)
- [x] **[MUST]** DeckService — 남의 덱 접근 시 403 (기존 테스트)

### 📓 학습 노트
- [ ] **[SHOULD]** week-2 ~ week-5 학습 노트
- [ ] **[SHOULD]** **닫고 다시 짜기 #1** (월말): JWT 로그인 흐름 빈 파일에서 90분 안에 재구현

### ✅ Phase 1 완료 기준
- [x] 회원가입/로그인/refresh/logout — 자동화 테스트 통과 (수동 시연은 Phase 2 진입 전 한 번)
- [x] refresh rotation + reuse detection 테스트 통과 (30/30)
- [x] 비밀번호 변경 후 기존 refresh 사용 불가 (`UserService.changePassword` + mass logout)
- [x] 회원 탈퇴 흐름 통과 (deletedAt + mass logout + 재로그인 차단)
- [ ] 인증 흐름을 그림으로 설명 가능 (수동 작업 — 면접 준비 단계에서)
- [x] `docs/auth-design.md` 작성 완료 (다이어그램 추가는 Phase 8에서)
- [ ] 면접 질문 5개 답변 가능 (week-N 학습 노트에 점진적 작성 중)

### 🆕 추가 아이디어
*(공란)*

---

## 🟢 Phase 2 — CRUD 강화 + 학습 모드 (4주)

> **목표:** "혼자 쓰는 단어장 앱" 완성 + Flashcard/Quiz/Typing/오답노트 흐름 정리.
> 반복 복습은 Phase 3에서 처리.
> **모드:** 🟢 A (대부분) + 🔵 B (Typing 채점 정책)

### 📚 Card 필드 확장 🟢
- [x] **[SHOULD]** Card에 `example_sentence`, `memo`, `position` 컬럼 추가
- [x] **[SHOULD]** `V4__extend_cards.sql` (V3는 users.deleted_at에 사용됨)
- [x] **[SHOULD]** `position` 기반 정렬 API — Card 검색의 `?sort=position` 옵션
- [ ] **[STRETCH]** 드래그 순서 변경 API

### 🔍 Card 검색/정렬 🟢
- [x] **[MUST]** `GET /decks/{deckId}/cards?keyword=&starred=` (front/back LIKE 검색, 대소문자 무시, 한국어 OK)
- [x] **[MUST]** 별표 카드만 필터 (기존 + search 메서드에 통합)
- [x] **[MUST]** 정렬 옵션 (생성일/위치/별표) — `?sort=createdAt|position|starred`, position은 NULL last

### 📥 일괄 등록 강화 🟢
- [ ] **[MUST]** 실패 라인 미리보기 응답
- [ ] **[MUST]** 최대 1000개 제한
- [ ] **[SHOULD]** 구분자 자동 감지 (탭/콤마/하이픈/콜론/파이프)
- [ ] **[SHOULD]** 중복 카드 처리 정책 (skip / overwrite 선택)
- [ ] **[STRETCH]** CSV 파일 업로드 (`multipart/form-data`)

### 🎯 Quiz 모드 강화 🔵
- [ ] **[MUST]** 퀴즈 세션 단위 관리 (`quiz_sessions`, `quiz_questions`)
- [ ] **[MUST]** `V4__add_quiz_sessions.sql`
- [ ] **[MUST]** 선택지 중복 제거 로직
- [ ] **[MUST]** 카드 수 5개 미만일 때 2~4지선다로 fallback
- [ ] **[MUST]** 정답 비교 정규화 (공백/대소문자)
- [ ] **[SHOULD]** 같은 세션 내 문제 중복 방지
- [ ] **[SHOULD]** `GET /api/quiz-sessions/{id}/summary`

### ⌨️ Typing 모드 🔵
- [ ] **[MUST]** `POST /api/decks/{deckId}/typing-sessions`
- [ ] **[MUST]** 사용자 입력 vs 정답 채점 (정확 일치 / 공백 무시 / 대소문자 무시)
- [ ] **[MUST]** 쉼표로 구분된 복수 정답 허용 (`사과, 능금`)
- [ ] **[MUST]** 채점 정책 문서화 (`docs/typing-policy.md`)
- [ ] **[STRETCH]** 오타 1~2개 허용 (Levenshtein)

### 📖 Flashcard 모드 정리 🟢
- [ ] **[SHOULD]** 기존 StudyService 흐름을 Flashcard 모드로 명확히 분리
- [ ] **[SHOULD]** 학습 방향 (front→back / back→front) 옵션
- [ ] **[SHOULD]** 세션 결과 요약 API

### 🗒️ 오답노트 🟢
- [ ] **[SHOULD]** 최근 틀린 카드 조회 API
- [ ] **[SHOULD]** 오답만 모아서 재퀴즈 생성

### 🧪 테스트
- [ ] **[MUST]** Quiz — 카드 부족 시 fallback 검증
- [ ] **[MUST]** Quiz — 선택지 중복 없음
- [ ] **[MUST]** Typing — 채점 정책 케이스 (대소문자/공백/복수정답)
- [ ] **[SHOULD]** Import — 중복 정책
- [ ] **[SHOULD]** Card — 검색/정렬

### 📓 학습 노트
- [ ] **[SHOULD]** week-6 ~ week-9 학습 노트
- [ ] **[SHOULD]** **닫고 다시 짜기 #2**: Typing 채점 로직 90분 재구현

### ✅ Phase 2 완료 기준
- [ ] 모든 MUST 항목 완료
- [ ] Flashcard / Quiz / Typing 3가지 모드 데모 페이지에서 동작
- [ ] 퀴즈 정답 조작 방지 로직 설명 가능
- [ ] 면접 질문 3개 답변 가능 (Typing 채점 정책 / 카드 부족 시 처리 등)

### 🆕 추가 아이디어
*(공란)*

---

## 🔵 Phase 3 — 반복 학습 알고리즘 (4주)

> **목표:** VocaMaster의 핵심 차별점. Leitner Box로 망각곡선 구현.
> **모드:** 🔵 B (전부 — 면접 메인 무기)

### 📊 Card Progress 모델
- [ ] **[MUST]** `card_progress` 테이블 설계 (user_id, card_id, box_level, next_review_at, correct_streak, wrong_count, last_reviewed_at, version)
- [ ] **[MUST]** `V5__add_card_progress.sql`
- [ ] **[MUST]** `(user_id, card_id)` unique 제약
- [ ] **[MUST]** `(user_id, next_review_at)` 복합 인덱스 (due 카드 조회용)
- [ ] **[MUST]** `CardProgress` 엔티티 + Repository
- [ ] **[MUST]** 사용자가 카드를 처음 만나면 자동 생성 (box=1, next_review_at=now)

### 🧠 Leitner Box 로직
- [ ] **[MUST]** 박스별 간격 정의 (`docs/review-algorithm.md`)
  - box 1: 10분 / box 2: 1일 / box 3: 3일 / box 4: 7일 / box 5: 14일 / box 6: 30일
- [ ] **[MUST]** `ReviewService.recordAnswer(cardId, correct)` — 박스 증감 + nextReviewAt 계산
- [ ] **[MUST]** 맞힘: box+1, 간격 증가
- [ ] **[MUST]** 틀림: box=1로 리셋, 짧은 간격
- [ ] **[MUST]** `@Version` 낙관적 락 적용 (동시 답변 충돌 방지)
- [ ] **[SHOULD]** OptimisticLockException 발생 시 409 응답 또는 1회 재시도
- [ ] **[STRETCH]** `Clock` 주입 (시간 의존 테스트 안정화)

### 🌐 API
- [ ] **[MUST]** `GET /api/reviews/due?deckId=` — 복습 대상 카드 목록
- [ ] **[MUST]** `POST /api/reviews/cards/{cardId}/answer` — 정답/오답 기록
- [ ] **[SHOULD]** `GET /api/reviews/today-summary` — 오늘 복습 카드 수 / 완료 수 / streak

### 🔥 연속 학습일 (Streak)
- [ ] **[SHOULD]** `daily_user_stats` 테이블
- [ ] **[SHOULD]** `V6__add_daily_stats.sql`
- [ ] **[SHOULD]** 학습 기록 시 오늘 날짜 stat 업데이트
- [ ] **[SHOULD]** streak 계산 로직 (어제 학습 → 오늘 학습 = streak+1)

### 🧪 테스트
- [ ] **[MUST]** 처음 카드 → progress 생성 확인
- [ ] **[MUST]** 맞힘 → box_level 증가, nextReviewAt 미래로
- [ ] **[MUST]** 틀림 → box_level=1, nextReviewAt 가까움
- [ ] **[MUST]** due cards만 조회되는지
- [ ] **[MUST]** 다른 사용자 progress와 분리되는지
- [ ] **[MUST]** 동시 답변 시 OptimisticLock 동작 확인
- [ ] **[SHOULD]** streak — 연속/비연속 케이스

### 📓 학습 노트
- [ ] **[SHOULD]** week-10 ~ week-13 학습 노트
- [ ] **[SHOULD]** **닫고 다시 짜기 #3**: Leitner 박스 증감 로직 90분 재구현
- [ ] **[MUST]** `docs/review-algorithm.md` — 면접 답변용 정리 (왜 Leitner? SM-2/FSRS와 차이?)

### ✅ Phase 3 완료 기준
- [ ] 모든 MUST 항목 완료
- [ ] 데모: 카드 답변 → 박스 변화 → 다음 복습 시점 변화 시연 가능
- [ ] 동시성 시나리오 1개 설명 가능 (왜 `@Version`? 충돌 시 어떻게?)
- [ ] due 쿼리 인덱스 설명 가능 (왜 복합 인덱스? 카디널리티?)
- [ ] 면접 질문 5개 답변 가능 (Leitner vs SM-2 vs FSRS / 왜 Leitner 선택? / 다음 단계 개선안?)

### 🆕 추가 아이디어
*(공란)*

---

## 🟢 Phase 4 — 공개 단어장 / 공유 (4주)

> **목표:** Quizlet 대체 느낌. 다른 사람이 쓸 수 있는 서비스.
> **모드:** 🟢 A (대부분) + 🔵 B (복사 시 데이터 일관성)

### 🔓 Visibility
- [ ] **[MUST]** Deck에 `visibility` 컬럼 추가 (`PRIVATE`/`PUBLIC`/`UNLISTED`)
- [ ] **[MUST]** `V7__add_deck_visibility.sql`
- [ ] **[MUST]** `PATCH /api/decks/{deckId}/visibility`

### 🔎 공개 단어장 검색 🟢
- [ ] **[MUST]** `GET /api/public/decks?keyword=&page=&size=` — 제목/설명 LIKE
- [ ] **[MUST]** `GET /api/public/decks/{deckId}` — 공개 덱 상세 조회
- [ ] **[MUST]** 비공개 덱 접근 시 **404** 처리 (403 X — 존재 노출 방지)

### 📎 단어장 복사 🔵
- [ ] **[MUST]** `POST /api/decks/{deckId}/copy` — 공개 덱을 내 덱으로 복사
- [ ] **[MUST]** 복사본은 PRIVATE으로 생성
- [ ] **[MUST]** 카드 전체 복제 (position 유지)
- [ ] **[MUST]** 원본 `copy_count` 증가 — DB **원자적 update** 사용 (race condition 방지)
- [ ] **[MUST]** `original_deck_id` 추적
- [ ] **[SHOULD]** 카드 0개인 공개 덱 복사 시 정책 결정 (허용/거부)
- [ ] **[SHOULD]** 복사 중 원본이 비공개로 전환된 경우 처리 (트랜잭션 격리)

### ❤️ 좋아요
- [ ] **[MUST]** `deck_likes` 테이블 (user_id, deck_id, created_at) — 복합 unique
- [ ] **[MUST]** `V8__add_deck_likes.sql`
- [ ] **[MUST]** `POST /api/public/decks/{deckId}/like` (idempotent)
- [ ] **[MUST]** `DELETE /api/public/decks/{deckId}/like`
- [ ] **[MUST]** Deck.like_count 동기화 (원자적 update)
- [ ] **[STRETCH]** like_count와 deck_likes 실제 개수 불일치 복구 스케줄러

### 📈 인기/최신 정렬
- [ ] **[SHOULD]** `GET /api/public/decks?sort=popular` — `like*5 + copy*3 + study*1` 점수
- [ ] **[SHOULD]** `GET /api/public/decks?sort=recent`

### 🏷️ 태그
- [ ] **[STRETCH]** `deck_tags` 테이블 (deck_id, tag_name)
- [ ] **[STRETCH]** `V9__add_deck_tags.sql`
- [ ] **[STRETCH]** 덱 생성/수정 시 태그 등록
- [ ] **[STRETCH]** `GET /api/public/decks?tag=toeic`

### 🧪 테스트
- [ ] **[MUST]** 복사 — 카드 개수 일치 / owner 변경 / copy_count 증가
- [ ] **[MUST]** 비공개 덱 복사 시 404
- [ ] **[MUST]** 좋아요 중복 시 idempotent
- [ ] **[SHOULD]** 자기 덱 좋아요 정책 (허용/거부 결정 + 테스트)

### 📓 학습 노트
- [ ] **[SHOULD]** week-14 ~ week-17 학습 노트
- [ ] **[SHOULD]** **닫고 다시 짜기 #4**: 단어장 복사 로직 90분 재구현

### ✅ Phase 4 완료 기준
- [ ] 모든 MUST 항목 완료
- [ ] 데모: 다른 계정의 공개 덱 → 검색 → 복사 → 좋아요 → 인기 목록 반영 시연
- [ ] 권한 정책 (PRIVATE/PUBLIC/UNLISTED) 설명 가능
- [ ] 복사 동시성 처리 설명 가능
- [ ] 면접 질문 3개 답변 가능 (왜 404? 왜 원자적 update? UNLISTED 의미?)

### 🆕 추가 아이디어
*(공란)*

---

## 🔵 Phase 5 — Redis (3주)

> **목표:** "왜 Redis 썼나요?"에 한 문장으로 답할 수 있는 사용처.
> **모드:** ⚪ C (Redis 자체 학습) → 🔵 B (적용)

### 🐳 Redis 인프라
- [ ] **[MUST]** `docker-compose.yml`에 Redis 서비스 추가 (개발용)
- [ ] **[MUST]** `spring-boot-starter-data-redis` 의존성
- [ ] **[MUST]** `RedisConfig` — Lettuce 기반, JSON 직렬화
- [ ] **[MUST]** `application-dev.yml` Redis 설정
- [ ] **[MUST]** `docs/redis-conventions.md` — key naming + TTL 정책 문서화

#### Redis Key 컨벤션 (예시)
```
login:fail:{email}:{ip}        TTL 30분
popular:decks:{yyyyMMdd}        TTL 7일
review:summary:{userId}:{date}  TTL 5분
```

### 🚪 로그인 실패 Rate Limit
- [ ] **[MUST]** `LoginAttemptService` — IP/email 기반 카운트
- [ ] **[MUST]** 5분 내 5회 실패 → 30분 잠금
- [ ] **[MUST]** `429 Too Many Requests` 응답
- [ ] **[MUST]** Redis 장애 시 fallback (rate limit 비활성화 + 로그 경고)

### 🏆 인기 단어장 캐시
- [ ] **[MUST]** Redis Sorted Set (`popular:decks:{yyyyMMdd}`)
- [ ] **[MUST]** 좋아요/복사/학습 발생 시 점수 업데이트
- [ ] **[MUST]** 캐시 무효화 정책 문서화 (`docs/cache-strategy.md`)
- [ ] **[MUST]** DB fallback 경로 (Redis 다운 시 DB ORDER BY)
- [ ] **[SHOULD]** 매일 자정 스코어 재계산 스케줄러

### ☀️ 오늘 복습 요약 캐시
- [ ] **[SHOULD]** `review:summary:{userId}:{date}` — 5분 TTL
- [ ] **[SHOULD]** 학습 기록 시 캐시 무효화

### 🧪 테스트 (Testcontainers 도입)
- [ ] **[SHOULD]** `testImplementation 'org.testcontainers:junit-jupiter'`
- [ ] **[SHOULD]** Redis Testcontainers 기반 통합 테스트 1개
- [ ] **[MUST]** Rate limit — 5회 실패 시 잠금
- [ ] **[MUST]** Rate limit — 시간 경과 후 해제
- [ ] **[MUST]** 인기 캐시 — 좋아요 시 점수 반영
- [ ] **[MUST]** Redis 다운 시 fallback 동작 확인

### 📓 학습 노트
- [ ] **[SHOULD]** week-18 ~ week-21 학습 노트
- [ ] **[MUST]** `docs/cache-strategy.md` 초안 (Phase 7에서 측정 결과 보강)

### ✅ Phase 5 완료 기준
- [ ] 모든 MUST 항목 완료
- [ ] Redis 사용처 3개 각각 "왜 썼는지" 한 문장 설명 가능
- [ ] Redis 다운 시뮬레이션 → 서비스 정상 동작 확인
- [ ] 면접 질문 5개 답변 가능 (Redis vs DB / 캐시 무효화 / TTL 정책 / 장애 fallback)

### 🆕 추가 아이디어
*(공란)*

---

## 🟢 Phase 6 — 비동기 이벤트 (3주)

> **목표:** API 응답 책임과 통계/배지 책임 분리. Spring Event부터 안정화.
> **모드:** 🟢 A (Spring Event) → ⚪ C (Kafka, 선택)

### 🔔 Spring ApplicationEvent
- [ ] **[MUST]** `CardAnsweredEvent`, `DeckCopiedEvent`, `DeckLikedEvent` 정의
- [ ] **[MUST]** `@TransactionalEventListener(phase = AFTER_COMMIT)` 사용 — 트랜잭션 커밋 후 처리
- [ ] **[MUST]** `@Async` 적용 + ThreadPoolTaskExecutor 설정
- [ ] **[MUST]** `@EnableAsync`
- [ ] **[MUST]** Async 리스너 예외 시 로깅 정책 (조용히 묻히지 않게)
- [ ] **[MUST]** 통계 집계 리스너 (daily_user_stats 갱신)
- [ ] **[SHOULD]** 인기 점수 갱신 리스너
- [ ] **[SHOULD]** 이벤트 실패 시 재처리 정책 — 문서만 작성, 구현은 선택

### 🏅 배지/업적
- [ ] **[STRETCH]** `badges` 테이블 + `user_badges` 테이블
- [ ] **[STRETCH]** 배지 규칙 (예: 7일 streak / 100카드 학습 / 첫 공개 덱)
- [ ] **[STRETCH]** 이벤트 리스너에서 배지 부여
- [ ] **[STRETCH]** `GET /api/users/me/badges`

### 📨 Kafka 도입 (조건부)
> ⚠️ Phase 1~5의 MUST 미완료가 있으면 Kafka 도입 금지. core 안정화된 경우에만.

- [ ] **[STRETCH]** docker-compose에 Kafka + Zookeeper 추가
- [ ] **[STRETCH]** `spring-kafka` 의존성
- [ ] **[STRETCH]** Spring Event → Kafka 메시지로 교체 (1개씩 점진)
- [ ] **[STRETCH]** Consumer 멱등성 보장 (이벤트 ID 중복 처리)
- [ ] **[STRETCH]** 실패 시 재시도 정책

### 🧪 테스트
- [ ] **[MUST]** 이벤트 발행 시 통계 갱신 확인
- [ ] **[MUST]** Async 처리로 API 응답 시간 영향 없음 확인
- [ ] **[MUST]** AFTER_COMMIT 동작 확인 (롤백된 트랜잭션은 이벤트 발행 X)
- [ ] **[STRETCH]** Kafka Consumer 중복 메시지 idempotent

### 📓 학습 노트
- [ ] **[SHOULD]** week-22 ~ week-25 학습 노트
- [ ] **[SHOULD]** `docs/event-architecture.md`

### ✅ Phase 6 완료 기준
- [ ] 모든 MUST 항목 완료
- [ ] AFTER_COMMIT 사용 이유 설명 가능 (트랜잭션 안 한 상태에서 이벤트 처리하면 무슨 문제?)
- [ ] Async 예외 처리 전략 설명 가능
- [ ] 면접 질문 3개 답변 가능 (왜 Spring Event부터? Kafka 안 쓴 이유 / 쓴 이유?)

### 🆕 추가 아이디어
*(공란)*

---

## 🔵 Phase 7 — 배포 / 성능 / 관측 (4주)

> **목표:** "실제로 띄워놓고 다른 사람이 쓸 수 있는" 상태.
> **모드:** ⚪ C (NewsPick 구조 학습) → 🔵 B (옮기기)

### 🐳 Docker
- [ ] **[MUST]** `Dockerfile` 멀티스테이지 빌드
- [ ] **[MUST]** `docker-compose.yml` (로컬 기본: app + mysql + redis)
- [ ] **[MUST]** `docker-compose.prod.yml` (운영: + nginx)
- [ ] **[MUST]** 헬스체크 설정

### 🌐 Nginx + HTTPS
- [ ] **[SHOULD]** `nginx/nginx.conf` 리버스 프록시
- [ ] **[SHOULD]** Let's Encrypt 인증서 발급
- [ ] **[SHOULD]** HTTPS 강제 리다이렉트
- [ ] **[SHOULD]** 보안 헤더 (HSTS, X-Frame-Options, X-Content-Type-Options, Referrer-Policy)

### 🔄 GitHub Actions CI/CD
- [ ] **[MUST]** `.github/workflows/ci.yml` — test + build
- [ ] **[SHOULD]** `.github/workflows/cd.yml` — main merge → EC2 SSH 배포
- [ ] **[MUST]** Secrets 설정 (GH_TOKEN, EC2_KEY 등)

### 📊 관측
- [ ] **[SHOULD]** `spring-boot-starter-actuator` + 필수 엔드포인트만 노출
- [ ] **[SHOULD]** 로그 설정 (`logback-spring.xml`) — 환경별 레벨
- [ ] **[STRETCH]** 요청 로깅 필터 (UserId/RequestId)

### 🚀 k6 부하 테스트
- [ ] **[MUST]** k6 설치 + `tests/k6/` 디렉토리
- [ ] **[MUST]** 시나리오: login / public deck list / quiz submit / due cards
- [ ] **[MUST]** k6 테스트 전 seed data 생성 스크립트 작성
- [ ] **[MUST]** **테스트 환경 스펙 기록** (EC2 타입, RAM, DB 위치)
- [ ] **[MUST]** **Redis 적용 전후 비교 측정** (p50, p95, p99)
- [ ] **[MUST]** 결과를 `docs/performance.md`에 기록 (실제 측정값)
- [ ] **[SHOULD]** 병목 1개 이상 찾아서 개선 사례 작성

### 🧪 테스트
- [ ] **[MUST]** CI에서 모든 테스트 통과
- [ ] **[SHOULD]** Testcontainers로 MySQL/Redis 통합 테스트 1개 이상

### 📓 학습 노트
- [ ] **[SHOULD]** week-26 ~ week-29 학습 노트
- [ ] **[MUST]** `docs/deployment.md` — 배포 트러블슈팅 기록

### ✅ Phase 7 완료 기준
- [ ] 모든 MUST 항목 완료
- [ ] 배포된 도메인 접속 가능 (HTTPS면 더 좋음)
- [ ] CI 그린 상태 1주 유지
- [ ] `docs/performance.md`에 실제 측정값 + 환경 스펙 기록
- [ ] 면접 질문 5개 답변 가능 (왜 멀티스테이지? p95 의미? 측정 환경 / Redis 효과 수치)

### 🆕 추가 아이디어
*(공란)*

---

## 🟢 Phase 8 — 마감 / 면접 준비 (3주)

> **목표:** 면접관이 보기 좋은 상태 + 내가 5분 안에 설명 가능한 상태.
> **모드:** 🟢 A 전부

### 📄 문서 마감
- [ ] **[MUST]** README 최종본 — 데모 URL / 핵심 기능 / 기술 스택 / 아키텍처 다이어그램 / 실행 방법
- [ ] **[MUST]** ERD 이미지 최신화
- [ ] **[MUST]** API 명세 (Swagger 캡처 또는 별도 문서)
- [ ] **[SHOULD]** 아키텍처 다이어그램 (`docs/architecture.png`)
- [ ] **[SHOULD]** 화면 캡처 / 시연 GIF (`docs/screenshots/`)
- [ ] **[SHOULD]** `docs/troubleshooting.md` — 8개월간 만난 트러블 5개 이상
- [ ] **[SHOULD]** `docs/limitations.md` — 한계와 향후 개선 계획

### 🎤 면접 대비
- [ ] **[MUST]** 핵심 질문 10개 답변 작성 (`docs/interview-qa.md`)
  - 왜 Redis를 썼는가 / Redis 장애 나면?
  - 반복 학습 알고리즘은 어떻게 설계했는가
  - 퀴즈 정답 조작을 어떻게 막았는가
  - 남의 단어장 접근을 어떻게 막았는가
  - 공개 단어장 복사는 어떻게 처리했는가
  - DB 인덱스는 어떻게 잡았는가
  - 테스트는 어떤 기준으로 작성했는가
  - 동시성 문제는 어떻게 해결했는가
  - Refresh token rotation은 왜 했는가
- [ ] **[SHOULD]** 추가 질문 20개 답변 (총 30개 목표)
- [ ] **[MUST]** 5분 발표 스크립트
- [ ] **[SHOULD]** 코드 리딩 시연 시나리오 (랜덤 파일 열어도 설명 가능)

### ✅ 최종 점검
- [ ] **[MUST]** 모든 테스트 통과
- [ ] **[MUST]** CI/CD 그린
- [ ] **[MUST]** 데모 사이트 안정 동작 1주
- [ ] **[MUST]** GitHub README의 모든 링크 동작 확인
- [ ] **[SHOULD]** 마지막 학습 노트 + 8개월 회고 (`docs/notes/retrospective.md`)
- [ ] **[SHOULD]** 핵심 도메인 테스트 충분성 점검 (Auth / Deck·Card 권한 / Import / Quiz / Review / Public Copy)
- [ ] **[STRETCH]** 코드 커버리지 측정
- [ ] **[STRETCH]** 핵심 도메인 80%+ 커버리지

### ✅ Phase 8 완료 기준 (= 프로젝트 완료 기준)
- [ ] 모든 MUST 항목 완료
- [ ] 면접관 앞에서 5분 안에 핵심 설명 가능
- [ ] 코드 임의 파일 열어도 흐름 설명 가능
- [ ] "이 프로젝트에서 가장 자랑스러운 부분 3가지" 즉답 가능
- [ ] "이 프로젝트의 한계 3가지" 즉답 가능

### 🆕 추가 아이디어
*(공란)*

---

## 📅 매월 의식 (Reminder)

- [ ] 월말 — 그 달 핵심 기능 "닫고 다시 짜기" 90분 훈련
- [ ] 월말 — `docs/notes/month-N-summary.md` 작성
- [ ] 월말 — 다음 달 Phase의 모드(A/B/C) 미리 결정
- [ ] 월말 — MUST 미완료 항목 점검 + 다음 달로 이월할지 판단

---

## 🚫 의도적으로 안 하는 것 (Out of Scope)

> 8개월 안에 욕심내면 망함. 면접관이 물으면 "범위 밖이라 빼고 핵심에 집중했다"로 답.

- ❌ MSA / 멀티 서비스 분리
- ❌ Kubernetes를 메인 배포로 (부록 문서로만 가능)
- ❌ Elasticsearch (MySQL FULLTEXT로 충분)
- ❌ 결제 / 구독
- ❌ 모바일 앱
- ❌ React 풀 SPA (Mustache UI + 최소 React 컴포넌트로 마감)
- ❌ AI/LLM 단어 자동 생성 (NewsPick과 차별화 위해)
- ❌ 실시간 협업 편집
- ❌ 소셜 팔로우 / 피드
- ❌ 알림 시스템 풀 구현 (이메일/푸시/인앱 다 X)

---

## 📚 메모리 연동

이 체크리스트와 함께 보면 좋은 메모리:
- `~/.claude/projects/.../memory/vocamaster_roadmap.md` — 월별 의도/이유
- `~/.claude/projects/.../memory/feedback_workflow.md` — A/B 모드 협업 규칙
- `~/.claude/projects/.../memory/portfolio_strategy.md` — 두 프로젝트 포지셔닝
