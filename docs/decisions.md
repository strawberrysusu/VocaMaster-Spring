# VocaMaster — Architecture Decision Records (ADR)

> 모든 *기능 선택*에는 *대안*이 있었다. *왜 이걸 골랐는지*를 기록해두면:
> - 면접에서 5분 답변 가능
> - 6개월 뒤 *내가 왜 이렇게 짰는지* 잊었을 때 복원
> - 새 멤버가 코드 읽을 때 *의도* 파악
>
> **운영 규칙:** 새 기능/기술 추가할 때마다 *결정 전*에 ADR 한 개 적기. 이 파일 또는 별도 `docs/decisions/ADR-NNN-제목.md`로 분리해도 OK.

---

## ADR-001: JWT를 Access(1h) + Refresh(14d)로 분리

**상태:** 채택 (2026-05, Phase 1)
**범위:** 인증 전반

### 컨텍스트
모든 API 호출이 인증 토큰을 요구함. *탈취 시 피해 시간* vs *사용자 편의*의 트레이드오프.

### 고려한 대안
- **A. 단일 장기 토큰 (예: 14일 JWT 1개)**
  - 단순. 한 번 발급하면 14일.
  - ❌ 탈취 시 14일 피해. JWT는 stateless라 *즉시 무효화 어려움*.
- **B. 단일 단기 토큰 (예: 1시간)**
  - 안전 ↑.
  - ❌ 사용자가 매시간 재로그인. UX 최악.
- **C. 세션 기반 (서버에 상태)**
  - 즉시 무효화 가능.
  - ❌ 서버 확장성 ↓ (sticky session 또는 공유 저장소 필요).
- **D. ✅ Access(짧음, stateless) + Refresh(김, DB stateful)**
  - 두 토큰의 *역할 분리*.

### 결정
**D. JWT split** — Access 1시간 / Refresh 14일.

### 근거
- Access는 짧고 stateless → 빠른 검증 + 탈취 시 *피해 시간 제한 (1시간)*
- Refresh는 길고 DB 저장 → *즉시 폐기 가능* (rotation / reuse detection / mass logout)
- 사용자 편의 (14일 안에 재로그인 X)

### 트레이드오프 / 한계
- Refresh 검증 시 DB 조회 (호출당 1회, 부담 작음)
- 클라이언트 복잡도 ↑ (두 토큰 관리)
- 만료된 access 동안 API 실패 → 클라가 자동 갱신 로직 필요

---

## ADR-002: Refresh Token을 SHA-256 해시로 DB 저장

**상태:** 채택 (2026-05, Phase 1)
**범위:** RefreshToken 엔티티

### 컨텍스트
Refresh token은 DB에 저장해야 (rotation/revocation을 위해). 그런데 *DB가 유출되면* 어떻게 보호?

### 고려한 대안
- **A. Raw 저장 (평문)**
  - 검증 단순.
  - ❌ DB 유출 = 모든 사용자 토큰 즉시 사용 가능. 치명적.
- **B. bcrypt 해시**
  - 비밀번호용. 매우 안전 (의도적으로 느림).
  - ❌ Refresh는 *매 호출마다 검증*인데 bcrypt는 수십~수백ms. 성능 ↓.
- **C. ✅ SHA-256 해시**
  - 빠른 단방향 해시.
  - Refresh는 256-bit random이라 brute-force 자체가 *천문학적 시간*. bcrypt 같은 *느림*이 필요 없음.

### 결정
**C. SHA-256** — `HexFormat.of().formatHex(SHA-256(token))` → 64자 hex로 저장 (CHAR(64)).

### 근거
- DB 유출 시 *raw 토큰 못 복원* → 즉시 사용 불가
- 검증 빠름 (수 μs 수준)
- Salt 불필요 (refresh는 256-bit random이라 동일 token 충돌 무시)

### 트레이드오프 / 한계
- bcrypt만큼 강력한 brute-force 저항은 아님 — 단 입력 엔트로피가 256-bit라 의미 X
- 비밀번호엔 절대 사용 금지 (password는 저엔트로피라 bcrypt 필수)

---

## ADR-003: Refresh Token을 httpOnly Cookie로 전달

**상태:** 채택 (2026-05, Phase 1)
**범위:** AuthController

### 컨텍스트
Refresh token을 *클라이언트에 어떻게 전달*할까? XSS 공격 표면 최소화가 목표.

### 고려한 대안
- **A. Response body로 반환, 클라가 localStorage 저장**
  - 단순.
  - ❌ XSS 공격이 JS로 localStorage 읽음 → refresh 탈취 → 영구 접근.
- **B. body 반환, sessionStorage 저장**
  - 탭 닫으면 사라짐.
  - ❌ XSS 동일 위험.
- **C. ✅ httpOnly Cookie + Path 제한 + SameSite**
  - JS에서 *못 읽음* (httpOnly). CSRF는 SameSite로 차단.

### 결정
**C. httpOnly Cookie**
- `httpOnly=true` (JS 차단)
- `Path=/auth` (다른 경로엔 자동 첨부 X)
- `SameSite=Lax` (dev) / `Strict` (prod, yml로 분기)
- `Secure=true` (prod), `false` (dev — localhost는 http)
- `Max-Age=14 days`

### 근거
- XSS로 refresh 못 훔침 (httpOnly)
- Path 제한 = `/auth/*`에만 첨부 → 다른 API에 노출 X
- prod의 `SameSite=Strict` = CSRF 거의 완벽 차단

### 트레이드오프 / 한계
- API-only 서버 (mobile app)에선 Cookie 처리 까다로움 → mobile은 Authorization 헤더로 별도 처리 필요
- CORS 환경에선 `withCredentials=true` 같은 클라 설정 필요
- dev/prod cookie 설정 분기 (yml `auth.cookie.secure/same-site`)

---

## ADR-004: Rotation을 Atomic UPDATE (CAS)로 처리

**상태:** 채택 (2026-05, Phase 1)
**범위:** RefreshTokenRepository.revokeIfActive

### 컨텍스트
같은 refresh로 *동시에 2번* refresh 호출되면 (탭 두 개 / 네트워크 재시도) — 두 번 다 회전 성공하면 안 됨 (race condition).

### 고려한 대안
- **A. SELECT-THEN-UPDATE (단순)**
  - SELECT 후 revoked_at 체크 → UPDATE.
  - ❌ TOCTOU race — 두 요청이 동시에 SELECT 통과 후 둘 다 UPDATE.
- **B. PESSIMISTIC_WRITE lock**
  - SELECT FOR UPDATE로 락 잡기.
  - ❌ 잠금 시간 ↑, 데드락 가능성, 처리량 ↓.
- **C. ✅ Atomic UPDATE (CAS, Compare-And-Swap)**
  - `UPDATE ... WHERE token_hash=? AND revoked_at IS NULL` 한 줄.
  - DB가 *한 번만* 성공시킴. affected rows = 1 → 회전 성공 / 0 → 누가 이미 처리.

### 결정
**C. Atomic UPDATE.**
```sql
UPDATE refresh_tokens SET revoked_at = NOW(), last_used_ip = ?
WHERE token_hash = ? AND revoked_at IS NULL
```

### 근거
- 락 없음 → 동시 처리량 유지
- DB의 *원자성*에 race 차단 위임
- affected rows로 race 우승자 명확히 판별 가능
- `affected=0` 분기에서 *reuse detection* 가능 (별도 SELECT로 revoked 여부 확인 — ADR-006)

### 트레이드오프 / 한계
- JPA의 `@Modifying` 사용 → `flushAutomatically=true` + `clearAutomatically=true` 필요 (영속성 컨텍스트 stale 문제 — 한 번 당해서 학습)
- NULL 비교 정확히 신경 (`revoked_at IS NULL`)

---

## ADR-005: 회원 탈퇴는 Soft Delete

**상태:** 채택 (2026-05, Phase 1)
**범위:** User 엔티티, UserService.deleteAccount

### 컨텍스트
사용자가 *회원 탈퇴* 시 데이터를 어떻게 처리할까?

### 고려한 대안
- **A. Hard Delete (row 통째로 삭제)**
  - 단순.
  - ❌ 복구 불가 (백업 외)
  - ❌ FK cascade 부담 (decks, cards, refresh_tokens 다 같이 삭제 or NULL 처리)
  - ❌ 통계 데이터 손실
- **B. Hard Delete + 익명화 (개인정보만 지움)**
  - GDPR 등 일부 규제 충족.
  - 복잡.
- **C. ✅ Soft Delete (deletedAt 박기)**
  - Row 유지, 시각만 기록.

### 결정
**C. Soft Delete.**
- `users.deleted_at DATETIME(6) NULL`
- `AuthService.login`에서 `isDeleted()` 체크로 로그인 차단
- 탈퇴 시 `revokeAllByUserId` (mass logout)

### 근거
- 실수 복구 가능 (deleted_at = NULL로 되돌리기)
- 통계 유지 (탈퇴자도 *과거 학습 기록*은 보존)
- FK cascade 부담 없음
- 이메일 reserved (재가입 시 `existsByEmail`로 차단)

### 트레이드오프 / 한계
- 조회 시 `WHERE deleted_at IS NULL` 필터 신경 — 잊으면 탈퇴자도 조회됨
- GDPR "잊혀질 권리" 강제 시 별도 hard delete 메서드 필요
- 이메일 영구 reserved = 재가입 불가 (정책 결정)

---

## ADR-006: 폐기된 Refresh 재사용 시 Mass Logout (Reuse Detection)

**상태:** 채택 (2026-05, Phase 1)
**범위:** AuthService.refresh

### 컨텍스트
Atomic UPDATE(ADR-004)에서 *affected=0*은 두 의미: (1) 토큰 없음 (2) 이미 폐기됨. (2)의 경우가 *탈취 신호*일 수 있음 — 공격자가 옛 refresh 들고 옴.

### 고려한 대안
- **A. 그냥 401 (그 토큰만 거부)**
  - 단순.
  - ❌ 공격자가 갱신은 못해도 *원래 사용자*가 알아채지 못함. 다른 활성 refresh 있으면 공격자도 시도 가능.
- **B. 그 refresh 1개만 폐기**
  - 이미 폐기된 거 또 폐기.
  - ❌ 의미 없음.
- **C. ✅ 그 사용자 모든 refresh 폐기 (Mass Logout)**
  - 양쪽 다 강제 재로그인 — 어느 쪽이 진짜인지 모르니 양쪽 다 끊음.

### 결정
**C. Mass Logout.**
```java
if (affected == 0) {
    Optional<RefreshToken> row = findByTokenHash(hash);
    if (row.isPresent() && row.get().isRevoked()) {
        revokeAllByUserId(userId, now);   // ← mass logout
        log.warn("Refresh token reuse detected ...");
    }
    throw new UnauthorizedException(...);
}
```

### 근거
- *어느 쪽이 진짜 사용자인지 모름* → 둘 다 강제 재로그인이 가장 안전
- 진짜 사용자: 한 번 재로그인 (불편) — 공격 인지 가능
- 공격자: 영구 차단

### 트레이드오프 / 한계
- 진짜 사용자도 *다른 디바이스 다 로그아웃* (UX ↓, 보안 ↑)
- 클라이언트 동시 호출 (탭 두 개 정상 사용) — 두 번째 호출이 reuse로 오인 가능 → 짧은 grace period 도입 검토 가능 (지금은 미적용)
- 응답 메시지는 *일반 401*과 동일 (공격자에게 단서 X), 로그는 WARN

---

## ADR-007: Account Enumeration 방지 — 메시지 통일

**상태:** 채택 (2026-05, Phase 1)
**범위:** AuthService.login

### 컨텍스트
로그인 실패 시 *어떤 메시지*를 줄까? 친절하게 알려주면 *어떤 이메일이 가입되어 있는지* 공격자가 알아냄 (account enumeration).

### 고려한 대안
- **A. 친절한 메시지 ("이메일 없음" / "비번 틀림" 분리)**
  - UX 친절.
  - ❌ 공격자가 이메일 dictionary로 *가입된 이메일 식별* 가능.
- **B. ✅ 모든 인증 실패에 통일 메시지**
  - "이메일 또는 비밀번호가 올바르지 않습니다" 한 가지.
  - 탈퇴된 사용자도 동일 메시지.

### 결정
**B. 통일 메시지.**

3가지 케이스 모두 동일:
- 이메일 없음
- 비번 틀림
- 탈퇴된 사용자

### 근거
- 공격자가 *어떤 이메일이 가입/탈퇴 됐는지* 못 알아냄
- OWASP 권장 패턴
- 메시지뿐 아니라 *응답 시간*도 비슷해야 완벽 (지금은 미고려 — 비번 검증 전후 차이로 timing 공격 이론상 가능, 매우 정밀해야 함)

### 트레이드오프 / 한계
- 사용자 친절도 ↓ (어떤 입력이 잘못됐는지 모름) — *Forgot Password* 흐름이 별도로 있어야 함
- 타이밍 공격 미고려 (실용적으론 영향 매우 작음)

---

## ADR-008: DB 스키마는 Flyway로 관리

**상태:** 채택 (2026-05, Phase 0)
**범위:** 전체 DB 스키마

### 컨텍스트
JPA `ddl-auto: update`로 시작했는데, 운영 환경에선 위험. 변경 이력 추적도 안 됨.

### 고려한 대안
- **A. ddl-auto: update 유지**
  - 단순.
  - ❌ 운영 DB가 코드 변경 따라 *멋대로* 바뀜. 컬럼 삭제 불가 (찌꺼기 누적). 환경 간 스키마 불일치 가능.
- **B. 수동 SQL (각 환경에서 직접 실행)**
  - 명시적.
  - ❌ 실수 가능성, 변경 이력 추적 X, CI/CD 어려움.
- **C. ✅ Flyway (코드 기반 마이그레이션)**
  - V1, V2, V3... 버전별 SQL 파일.
- **D. Liquibase (XML 기반)**
  - Flyway보다 강력하지만 학습 비용 ↑.

### 결정
**C. Flyway.**
- `src/main/resources/db/migration/V*.sql`
- dev/prod: `ddl-auto: validate` + Flyway enabled
- test: H2 + `ddl-auto: create-drop` + Flyway disabled

### 근거
- 변경 이력이 *코드와 함께* git에 박힘
- 환경 간 스키마 일관성 (V1~V4 다 적용됐는지 자동 검증)
- `ddl-auto: validate`로 엔티티-DB 매핑 부팅 시 검증 → 사고 방지
- Flyway는 Liquibase보다 단순

### 트레이드오프 / 한계
- **절대 규칙:** 적용된 V*.sql *수정 금지* (checksum mismatch) → 변경은 새 V{n+1} 추가
- 초기 도입 비용 (V1 만들 때 기존 엔티티 SQL 직접 작성)
- 롤백 자동화 안 됨 (롤백 SQL은 수동 작성)

---

## ADR-009: 커스텀 예외 4개 분리

**상태:** 채택 (2026-05, Phase 0)
**범위:** common/exception/

### 컨텍스트
서비스에서 비즈니스 예외를 어떻게 던질까? GlobalExceptionHandler에서 어떻게 잡을까?

### 고려한 대안
- **A. 단일 RuntimeException + 메시지 파싱**
  - 클래스 1개.
  - ❌ 메시지 텍스트로 HTTP status 매핑 → 깨지기 쉬움, 다국어 지원 어려움.
- **B. Spring의 `ResponseStatusException(HttpStatus.XXX, ...)`**
  - 표준.
  - ❌ Service 코드에 HTTP status 박힘 (도메인 코드 ↔ HTTP 결합)
- **C. ✅ 4개 커스텀 예외 분리**
  - NotFoundException (404), ForbiddenException (403), BadRequestException (400), UnauthorizedException (401)
  - 각각 RuntimeException 상속.

### 결정
**C. 4개 분리.**
- GlobalExceptionHandler가 *타입으로 자동 매핑*
- Service 코드는 `throw new NotFoundException(...)` 한 줄

### 근거
- 타입 자체가 *의미*를 표현 (HTTP status 노출 X)
- GlobalExceptionHandler에서 `@ExceptionHandler(NotFoundException.class)` 깔끔
- 메시지 텍스트 파싱 안 해도 됨

### 트레이드오프 / 한계
- 새 종류 필요할 때마다 클래스 추가
- 일부 큰 서비스 (DeckService, QuizService, StudyService)는 *아직 옛 ResponseStatusException 남음* → 청소 거리 (Phase 0 Day 6 부분 적용)

---

## ADR-010: CustomUserDetails + JWT 클레임만 사용 (DB 조회 제거)

**상태:** 채택 (2026-05, Phase 1)
**범위:** JwtAuthFilter, CustomUserDetails

### 컨텍스트
매 API 요청마다 사용자 정보를 어디서 가져올까?

### 고려한 대안
- **A. 매 요청마다 UserRepository.findById**
  - 항상 최신 정보.
  - ❌ 모든 API 호출마다 DB 조회 1번 → 부담 ↑, 성능 ↓
- **B. ✅ JWT 클레임만 사용 (DB 조회 0)**
  - JWT에 userId + email 있음 → SecurityContext에 박음
- **C. 캐싱 레이어 (Redis 등)**
  - 빠르지만 인프라 ↑.

### 결정
**B. JWT 클레임만.**
- `CustomUserDetails(userId, email)`만 SecurityContext에 박음
- 필요한 도메인 (예: /users/me)에서만 `UserRepository.findById` 호출

### 근거
- 매 요청 DB 부담 0
- JWT 검증만으로 인증 완료 (Stateless)
- `type=access` 검증으로 refresh 토큰 차단 (이중 방어)

### 트레이드오프 / 한계
- 사용자 정보 변경 즉시 반영 X (다음 access 발급 시까지 stale 가능, 최대 1시간)
- 닉네임 보여줄 땐 별도 DB 조회 (예: `GET /users/me`)
- 권한 변경 즉시 반영 X (mass logout 또는 access 만료 대기)

---

## ADR-011: yml을 dev/test/prod로 Profile 분리

**상태:** 채택 (2026-05, Phase 0)
**범위:** application*.yml

### 컨텍스트
환경마다 *DB 연결*, *비밀 정보*, *로그 레벨*이 다름. 어떻게 관리?

### 고려한 대안
- **A. 단일 yml + 코드 분기**
  - 한 파일.
  - ❌ 코드에 환경 분기 박힘, 비밀 정보 분리 어려움.
- **B. 환경변수만으로 모든 설정**
  - 12-factor 정공법.
  - ❌ 개발 환경에서도 환경변수 박아야 함 → 불편.
- **C. ✅ Spring Profile + yml 분리**
  - dev/test/prod 각각 yml 파일.

### 결정
**C. Profile 분리.**
- `application.yml`: 공통 (profile 활성화, mustache, server.port)
- `application-dev.yml`: 로컬 MySQL (값 yml에 직접, ddl-auto: validate, show-sql: true)
- `application-test.yml`: H2 + ddl-auto: create-drop + Flyway off
- `application-prod.yml`: 전부 `${ENV_VAR}` (비밀 정보 git에서 분리)

### 근거
- 명시적, Spring 표준
- prod의 비밀 정보 git 노출 차단
- dev에선 yml에 직접 값 둬도 OK (개발 편의)

### 트레이드오프 / 한계
- **함정:** `src/test/resources/application.yml`이 main의 application.yml *덮어씀* → 새 키 추가 시 양쪽 동기화 필요 (이미 두 번 당함 — jwt.expiration, auth.cookie)
- prod 시작 시 모든 env var 필수 (없으면 부팅 실패)

---

## ADR-012: Pagination Size를 1~100으로 Cap

**상태:** 채택 (2026-05, Phase 1)
**범위:** common/PageableUtils

### 컨텍스트
클라이언트가 `?size=999999` 같은 큰 값 보내면 OOM 또는 장기 쿼리.

### 고려한 대안
- **A. 무제한 (클라 요청 그대로)**
  - 단순.
  - ❌ 악의적/실수 큰 요청에 서버 다운 가능.
- **B. 고정 size (예: 20)**
  - 안전.
  - ❌ 클라가 *유연성 X*. 작은 size도 못 쓰니 over-fetch.
- **C. ✅ 1~100 동적 cap**
  - `Math.max(1, Math.min(100, size))`

### 결정
**C. Cap.**
- 최소 1, 최대 100
- page는 음수면 0으로

### 근거
- OOM 방지 (한 요청에 최대 100 row)
- 장기 쿼리 방지
- 클라이언트 유연성 유지 (1~100 안에서 자유)

### 트레이드오프 / 한계
- 100 초과 필요한 케이스 (예: export) 별도 endpoint 필요
- "100"이라는 magic number — 환경별 조정 가능하게 만들 수도

---

## ADR-013: Card 검색은 단일 동적 쿼리 (조건부 NULL)

**상태:** 채택 (2026-05, Phase 2)
**범위:** CardRepository.search

### 컨텍스트
Card 검색 = keyword + starred + 정렬 조합. 어떻게 처리?

### 고려한 대안
- **A. Repository 메서드 4개 분리**
  - findByDeckId / findByDeckIdAndStarred / searchByKeyword / searchByKeywordAndStarred
  - ❌ 중복 코드, 조건 추가 시 폭발 (2^n)
- **B. Spring Data Specification (동적 쿼리)**
  - 매우 유연.
  - ❌ 학습 비용 ↑, 코드 복잡
- **C. ✅ 단일 `@Query` JPQL + 조건부 NULL 패턴**
  - `(:keyword IS NULL OR ...)` 한 쿼리에 다 처리

### 결정
**C. 단일 쿼리.**
```sql
WHERE c.deck.id = :deckId
  AND (:keyword IS NULL OR LOWER(c.front) LIKE ... OR LOWER(c.back) LIKE ...)
  AND (:starred IS NULL OR c.starred = :starred)
```

### 근거
- 코드 단순 (메서드 1개)
- 옵션 추가 시 한 쿼리에 한 줄
- Specification 학습 비용 회피

### 트레이드오프 / 한계
- 조건 많아지면 쿼리 복잡 (5개 이상 옵션이면 Specification 권장)
- DB 옵티마이저가 `NULL OR ...` 패턴을 *항상 효율적으로* 처리하진 않음 (대부분 OK, 가끔 인덱스 미적용)

---

## ADR-014: Position 정렬은 NULL Last 명시

**상태:** 채택 (2026-05, Phase 2)
**범위:** CardService.resolveSort

### 컨텍스트
`cards.position`은 NULL 허용. 정렬 시 NULL 어디로 보낼까?

### 고려한 대안
- **A. MySQL 기본 (ASC=NULL first, DESC=NULL last)**
  - 단순.
  - ❌ 의도: "position 정한 카드 먼저" → ASC에서 NULL이 앞에 오면 의도와 반대.
- **B. ✅ NULL last 명시 (`Sort.Order.asc("position").nullsLast()`)**
  - 의도와 일치.
- **C. position 정렬 시 NULL을 큰 값으로 대체 (COALESCE)**
  - 작동.
  - ❌ DB-specific 함수, JPQL 깨끗하지 않음.

### 결정
**B. NULL last 명시.**

### 근거
- 의도 명확: "사용자가 position 정한 카드 먼저, 안 정한 건 뒤로"
- DB별 NULL 처리 차이 (MySQL/PostgreSQL/H2 다름) 영향 X — 명시했으니 일관

### 트레이드오프 / 한계
- 모든 nullable 정렬 필드마다 명시 필요 (불편하지만 안전)
- DB 인덱스에 따라 NULLS LAST가 인덱스 안 탈 수도 있음 (성능 이슈 시 monitoring 필요)

---

## ADR-015: 만료 Refresh 테스트는 @TestPropertySource로 시간 단축

**상태:** 채택 (2026-05, Phase 1)
**범위:** ExpiredRefreshTest

### 컨텍스트
"만료된 refresh를 거부하는지" 검증하려면 14일을 기다릴 수 없음. 시간을 어떻게 조작?

### 고려한 대안
- **A. `Clock` 주입 (production 코드 변경)**
  - 모든 시간 의존 코드를 `Clock`으로 주입받게 변경.
  - 매우 강력, 시간 100% 통제.
  - ❌ Production 코드 큰 변경, 도입 비용 ↑.
- **B. `Mockito.mockStatic(System.class)`**
  - 정적 메서드 mock.
  - ❌ 복잡, Mockito 5+ 필요, 불안정.
- **C. ✅ `@TestPropertySource(properties = "jwt.refresh-expiration=1")` + 짧은 sleep**
  - 그 테스트 클래스에서만 만료를 1ms로 override.
  - 다른 테스트 영향 X.

### 결정
**C. @TestPropertySource.**
```java
@TestPropertySource(properties = "jwt.refresh-expiration=1")
class ExpiredRefreshTest {
    @Test void refresh_expired_rejected() {
        TokenPair pair = authService.register(...);
        Thread.sleep(50);  // 확실히 만료
        assertThrows(UnauthorizedException.class, () -> authService.refresh(...));
    }
}
```

### 근거
- Clock 주입은 *production 코드 큰 변경* — 만료 검증 *하나*를 위해 과한 투자
- yml override는 *그 테스트만* 영향, 다른 테스트 격리됨
- 단순 (한 줄 어노테이션)

### 트레이드오프 / 한계
- `Thread.sleep`은 테스트 시간 ↑ (50ms 추가)
- 정밀 시간 제어 X (만료 *직전*/*직후* 같은 세밀한 시나리오 어려움)
- 시간 의존 로직이 더 복잡해지면 (예: Leitner Box 반복 학습) Clock 주입 도입 권장

---

## ADR-016: 프론트엔드 — Mustache 메인 + 후반 React 핵심 화면 (AI 작성, 백엔드 깊이 우선)

**상태:** 채택 (2026-05-12, 도입 시점: Phase 5 이후)
**범위:** 프론트엔드 전체

### 컨텍스트
현재 Mustache로 데모 UI만 있음. 어느 정도까지 React로 전환할지 결정 필요.

**상황 변수:**
- 사용자 = 백엔드 취준생, React 초보
- 8개월 안에 백엔드 핵심 (Phase 3 반복학습 / 5 Redis / 6 비동기 / 7 배포) 마감
- NewsPick (포트폴리오 1번)에 *React + Spring 풀스택* 경험 이미 있음
- VocaMaster (포트폴리오 2번) = *백엔드 깊이*로 차별화 포지셔닝

### 고려한 대안
- **A. Mustache 유지 끝까지**
  - ✅ 백엔드 100% 집중
  - ❌ 모던 웹 인상 약함, 진짜 REST API 사용처 없음
- **B. 즉시 React 도입 (Phase 2 직후)**
  - ✅ 모던 풀스택
  - ❌ 백엔드 시간 마이너스, React 초보라 학습 비용 ↑↑
- **C. ✅ Mustache 유지 + 후반 핵심 화면만 React (NewsPick 패턴)**
  - 학습/퀴즈/결과 3~5 화면만 React 컴포넌트
  - Spring static에 빌드 결과 번들
- **D. Mustache + Postman/Swagger 시연만**
  - ✅ 시간 최대 절약
  - ❌ 시각적 임팩트 약함

### 결정
**C. 추가 규칙:**
1. **도입 시점:** Phase 5 (Redis) 이후 또는 Phase 7 (배포) 사이
2. **범위:** 핵심 화면 3~5개만 (학습 / 퀴즈 / 결과 / 통계 정도)
3. **풀 SPA X** — Mustache와 공존
4. **모드:** React 코드는 *AI 작성, 사용자는 읽기*. 백엔드는 사용자 직접 타이핑 (새 모드 그대로)

### 근거
- VocaMaster의 *차별화 무기 = 백엔드*. React 깊이로는 NewsPick과 차별화 X.
- NewsPick에서 React 풀스택 경험 이미 있음 → "React 협업 경험" 면접 어필은 그쪽으로 충분
- 8개월 안에 Phase 3/5/6/7 백엔드 핵심 *깊게* 가려면 React에 큰 시간 투자 어려움
- React는 *AI 활용에 적합한 영역*임을 의식적으로 선택 — 학습 가치 vs 시간 자원의 *의도적 트레이드오프*
- 사용자 직접 결정 (2026-05-12): "백엔드는 깊이있게 끝까지, React는 최소한 + AI로 처리"

### 트레이드오프 / 한계
- React 자체 깊이는 *NewsPick 수준*에서 멈춤 (의도적)
- VocaMaster의 React는 *백엔드 검증용*에 가까움 (CORS / httpOnly Cookie 동작 검증 / 토큰 자동 갱신 인터셉터 / SPA에서 인증된 API 호출 시연)
- 두 시스템 공존 (Mustache + React) → README에 "왜 둘 다인지" 설명 필요
- 면접에서 React 깊은 질문 들어오면 NewsPick으로 답변 유도 (역할 분담 명확히)
- 만약 채용 시장이 *React/Vue 전문성* 강하게 요구하는 쪽으로 바뀌면 ADR 재검토

### 도입 시 구체 계획 (Phase 5 이후 시작 시)
- 스택: React + TypeScript + Vite (NewsPick과 동일)
- 빌드 통합: Spring Boot 빌드 시 React 결과물을 `src/main/resources/static/react/`로
- 라우팅: 2~3개 (학습 / 퀴즈 / 결과)
- 인증: httpOnly Cookie 자동 첨부 + access token 인터셉터 (자동 갱신)
- 예상 시간: 4~6주

---

## ADR-017: TTS — 비공식 Google Endpoint 시작 + Redis 캐싱으로 진화

**상태:** ~~채택 (2026-05-12)~~ → **개정 (2026-08-23, Codex 검산)** — 아래 "개정" 참조. 원문은 기록용으로 보존.

> **개정 (2026-08-23):** 비공식 endpoint는 약관 위반 + 예고 없는 차단(403/캡차) 위험이라 **공개 서비스의 핵심 기능으로 부적합**. 또한 "프론트 직접 호출"과 "Redis 캐싱"은 한 경로가 아니라 백엔드 프록시 전환이 필요한 별개 구조였음(원문의 설계 결함).
> **새 결정: 브라우저 내장 `speechSynthesis` = "기기 의존 브라우저 TTS"** (구글 번역 음성 확보가 아니라 **사용자 브라우저·OS에 설치된 음성을 쓰는 것** — Codex 표현 정정). `frontend/src/lib/tts.ts` `speak(text, lang)` 한 곳. Chrome은 "Google US English"(구글 네트워크 음성), Edge는 "Microsoft … Online (Natural)"(Azure 신경망 — 실청취 결과 번역기보다 낫다는 평) 등 **있는 것 중** 이름 우선순위(Google → Natural → Microsoft)로 선택, 언어는 텍스트 휴리스틱(가나·한자=ja, 한글=ko, 그 외=en). **비용 0, 서버 0, Redis 불필요.** 한계: **기기마다 목소리 다름·동일 음성 보장 없음**, 네트워크 음성은 오프라인 불가, 한자만 있는 텍스트는 중국어여도 일본어로 판별, 재생 실패 시 안내 없음(백로그). 🔊는 반드시 카드 button의 **형제**로 배치 — 중첩 시 HTML 위반 + Enter/Space 전파. **업그레이드 경로:** 공개 서비스 단계에서 공식 Google Cloud TTS(무료 구간 존재, 결제 계정 필요 — 금액은 당시 요금표로 재확인)를 백엔드에서 호출하도록 `speak()` 구현만 교체. 대안 비교: ① 브라우저(채택) ② 공식 Cloud TTS(결제 계정 장벽) ③ 비공식(철회).
**범위:** 프론트엔드 + Phase 5 Redis

### 컨텍스트
영어 단어 발음 듣기. Web Speech API 음질 별로라 *Google 번역기 수준* 원함. 비용/약관/유지보수 트레이드오프.

### 고려한 대안
- **A. ✅ 비공식 Google Translate TTS endpoint**
  - `https://translate.google.com/translate_tts?ie=UTF-8&q={text}&tl=en&client=tw-ob`
  - 프론트 한 줄, 음질 = 번역기 그대로
  - ❌ 비공식 (차단 가능), 약관 회색
- **B. Google Cloud TTS (공식)**
  - WaveNet 음성 = 번역기와 같은 엔진
  - ❌ 결제 정보 등록 필요 (월 4M자 무료)
- **C. ✅ A + Redis 캐싱 (같은 단어 재사용)**
  - 단어 텍스트 → Redis 키 → mp3 URL/bytes 캐시
  - 두 번째 호출부터 캐시 hit, Rate limit 회피
- **D. Web Speech API** — 음질 별로 (거부됨)
- **E. Amazon Polly** — B와 유사, AWS 의존
- **F. 미리 녹음된 mp3** — 단어 수만큼 저장 부담

### 결정
**A로 시작 → Phase 5에서 C (Redis 캐싱) 도입.**

### 근거
- 학습/포트폴리오 단계엔 비공식 endpoint *충분* (실 서비스 트래픽 X)
- Phase 5 Redis 작업과 *자연스럽게 결합* — 인기 단어장 캐시와 같은 패턴
- 비용 0, 결제 정보 X
- 음질 = Google 번역기 그대로
- 트래픽 폭증 시 B로 전환 옵션 열어둠

### 트레이드오프 / 한계
- 비공식 API → 차단/변경 가능 (포트폴리오 단계엔 영향 최소)
- Rate limit (캐싱으로 호출 ↓)
- 면접 질문 "왜 비공식?" → "학습 단계의 의식적 선택, prod 진입 시 B/Polly로 전환 계획"

### 진화 경로
1. **Phase 5 React 도입 시:** 프론트가 비공식 endpoint 직접 호출 (백엔드 0)
2. **Phase 5 Redis 작업 시:** 단어 텍스트 → Redis 캐싱 (같은 단어 두 번째부터 캐시)
3. **트래픽 늘면:** Google Cloud TTS + 캐싱으로 전환 (코드 변경 작음)

---

## ADR-018: 콘텐츠 타입 다양화 — JPA 상속(JOINED)으로 별도 도메인

**상태:** 채택 (2026-05-12, Phase 2 마지막 또는 Phase 3 시작에 도입)
**범위:** Card 도메인 → ContentItem 도메인으로 확장

### 컨텍스트
현재 `Card` = 단어 카드 전용 (front/back). 사용자 요구: **독해 / 문법 / 빈칸 채우기** 등 다양한 문제 유형도 같은 단어장에 담고 학습/공유 가능해야 함.

### 고려한 대안
- **A. Card에 `type` 필드 + 모든 옵션 필드 nullable**
  - 한 테이블에 다 박음 (`passage_text VARCHAR(2000) NULL` 등)
  - ✅ 단순, 빠른 도입
  - ❌ 모든 타입의 필드가 한 테이블 — 지저분, NULL 폭증
- **B. ✅ JPA 상속 (`@Inheritance(JOINED)`) + 자식 엔티티들**
  - 부모: `ContentItem` (id, deck, type, createdAt, updatedAt)
  - 자식: `WordCard`, `PassageItem`, `GrammarItem`, `FillBlankItem`
  - 각자 자기 테이블 + 부모 테이블 (JOIN으로 조회)
- **C. Card 그대로 + 완전 별도 도메인** — Deck이 여러 도메인 못 담음
- **D. JSON 필드 (`content_json`)** — 유연성 최대, 스키마 강제 X

### 결정
**B. JPA 상속 (JOINED).**
- 부모: 추상 `ContentItem` — 공통 필드 (id, deck, type, position, starred, createdAt, updatedAt)
- 자식: 각 타입별 자기 필드만

### 근거
- 깔끔한 도메인 모델 — 각 타입이 *자기 필드만* 가짐
- 학습 가치 ↑ — JPA 상속 패턴은 면접 질문 단골
- Repository / Service에서 *공통 처리* (학습 흐름)와 *타입별 처리* (퀴즈 생성 방식) 분리 가능
- 새 타입 추가 = *자식 클래스만* 추가
- Polymorphism으로 Deck.items 다형성 활용

### 트레이드오프 / 한계
- JOINED = JOIN 쿼리 증가 (학습 서비스 규모에선 무관, 인덱스로 해결)
- `Card` → `WordCard` rename 마이그레이션 분량 큼 (~2주 예상)
- `Deck.cards` 관계 → `Deck.items` (ContentItem 컬렉션)로 일반화 필요
- Quiz/Study 서비스가 *모든 타입 지원*하도록 확장 (점진적, 처음엔 WordCard만)

### 도입 계획
1. **Phase 2 마지막** (Card 변경 영향 최소화):
   - V5 마이그레이션: `content_items` 테이블 + `word_cards` 테이블 (Card → WordCard 이전)
   - `ContentItem` 추상 부모 + `WordCard` 자식
   - 기존 `Card` 코드 → `WordCard`로 rename + 호환 alias 유지 (점진 변경)
2. **Phase 3 시작 전:** Quiz/Study 서비스 ContentItem 기반으로 일반화
3. **Phase 4 사이:** `PassageItem` 추가 (독해 첫 타입)
4. **Phase 5~:** `GrammarItem`, `FillBlankItem` 점진

### 면접 답변 거리
> "단어 카드만 있던 초기 모델에서 독해/문법 등 다양한 콘텐츠 요구가 늘 거라 JPA `@Inheritance(JOINED)`로 추상 `ContentItem` + 자식 분리. 각 타입의 필드 격리 + 다형성 학습 흐름."

---

## ADR-019: 게이미피케이션 — Quest 도메인 (별도 시스템)

**상태:** 채택 (2026-05-12, Phase 6)
**범위:** 사용자 진척도 / 미션 시스템

### 컨텍스트
사용자 요구: "게임 진척도 깨는거마냥" — 명시적 미션 + 진행도 추적 + 보상.

### 고려한 대안
- **A. Phase 6 배지에만 통합 (조건 자동 지급)**
  - 단순
  - ❌ 능동적 *진행도 추적* 없음, 사용자가 "5/20 완료" 같은 진행 못 봄
- **B. ✅ 별도 Quest 도메인**
  - `quests` 테이블 — 시스템 정의 미션
  - `user_quest_progress` — 사용자별 진행도
  - 학습 이벤트 시 자동 갱신
- **C. 미션 없이 streak만** — 사용자 의도와 안 맞음

### 결정
**B. Quest 도메인 신설.**

### 근거
- 사용자 의도와 정확히 일치 (RPG 스타일 진척도)
- Phase 6의 *이벤트 기반 아키텍처* 와 자연스럽게 결합 — `CardStudiedEvent`, `QuizAnsweredEvent` 리스너로 progress 갱신
- 시스템 미션 정의 → 진행 추적 → 완료 시 배지 → 게임화 흐름 명확
- 면접 답변 거리 (이벤트 리스너 + 비동기 처리 + 진척도 데이터 모델)

### 트레이드오프 / 한계
- Phase 6 분량 +1주
- 시스템 미션은 *코드/SQL로 박힌* 정의 — 동적 추가하려면 관리자 UI (현재 X)
- 사용자 정의 미션 (스스로 목표 설정)은 STRETCH

### 시스템 미션 예시 (Phase 6 INSERT)
- `DAILY_20` — 오늘 카드 20개 외우기
- `STREAK_7` — 연속 7일 학습
- `QUIZ_PERFECT_5` — 퀴즈 5문제 연속 정답
- `COMPLETE_DECK` — 단어장 1개 완주 (모든 카드 known)
- `SHARE_DECK` — 공개 단어장 1개 만들기
- `COPY_5` — 다른 사용자 단어장 5개 복사

### 데이터 모델
```sql
-- quests: 시스템 정의 미션
id, code (UNIQUE), title, description, target_value, reward_badge_id, created_at

-- user_quest_progress: 사용자별 진행도
id, user_id, quest_id, current_value, completed_at, created_at, updated_at
UNIQUE (user_id, quest_id)
```

### 이벤트 처리 흐름
```
CardStudiedEvent (사용자 카드 학습)
  → @TransactionalEventListener(AFTER_COMMIT)
  → 해당 사용자의 활성 quests 중 *카드 학습* 카운트 quest 찾음
  → current_value++ → target 달성 시 completed_at + 배지 지급
```

---

## ADR-020: 일괄 등록 — 1회 입력 1000줄 상한 + 초과 시 전체 거부

**상태:** 채택 (2026-05-18)
**범위:** `ImportService` (텍스트 일괄 등록)

### 컨텍스트
`importCards`가 입력 줄 수에 *상한이 없음*. 사용자가 10만 줄 텍스트를 붙이면 10만 번 `save` → 서버 부하 / 응답 지연 / 잠재 OOM. 상한이 필요.

### 고려한 대안
- **A. ✅ 전체 거부** — 초과 시 `BadRequestException`, 한 건도 저장 X
  - 명확. 사용자가 *알고* 텍스트를 나눠서 다시 올림
- **B. 1000개까지 자르고 나머지 failed 처리**
  - ❌ 데이터 일부 누락을 사용자가 *조용히* 놓칠 위험
- **C. 1000개 저장 + 경고 메시지**
  - ❌ 응답 구조 복잡 + B와 같은 누락 위험

### 결정
**A. 전체 거부.** 카운트 기준은 **입력 줄 수** (빈 줄·실패 줄 포함) — 파싱 *전에* 컷.

### 근거
- "조용한 잘림"은 데이터 손실을 만듦 → 명시적 거부가 안전
- 파싱 전에 줄 수로 컷 → 거대 입력을 *파싱조차 안 함* (더 빠르고 안전)
- 1000줄이면 일반 단어장 용도로 차고 넘침 (Quizlet도 1세트 수백 단어 수준)

### 트레이드오프 / 한계
- 사용자가 1000줄 초과 텍스트는 직접 나눠 두 번 올려야 함 (UX 약간 불편 — 단 흔치 않은 케이스)
- 줄 수 기준이라 "성공 카드 1000개"가 아니라 "입력 줄 1000개" — 빈 줄 많으면 실제 카드는 더 적을 수 있음 (의도된 단순화)

---

## ADR-021: 테스트 작성 정책 — 핵심 + 경계만, 단순 CRUD 제외

**상태:** 채택 (2026-05-18)
**범위:** 전체 테스트 코드

### 컨텍스트
모든 기능에 테스트를 다 짜면 개발 속도가 크게 떨어짐. 안 짜면 면접 약점 + 회귀 버그 위험. *어디에 테스트를 쓸지* 기준이 필요.

### 고려한 대안
- **A. 모든 기능 테스트 (커버리지 100% 지향)**
  - ❌ 느림. 단순 CRUD까지 다 테스트 = 시간 낭비
  - ❌ 면접에서 *판단력*이 안 보임 ("그냥 다 했어요")
- **B. ✅ 핵심 비즈니스 로직 + 경계 조건만**
  - 테스트 *대상을 판단해서* 고름
- **C. 테스트 최소화 / 거의 안 함**
  - ❌ 면접 약점 + 회귀 버그 추적 비용 큼

### 결정
**B. 핵심 + 경계만.**

**테스트 *꼭* 쓰는 곳:**
- 보안 — 인증, 권한 체크 (남의 자원 접근 차단 등)
- 복잡한 비즈니스 규칙 — rotation, reuse detection, mass logout
- 경계 조건 — 1000줄 상한, 0개, 최댓값, 빈 입력
- 회귀 위험이 큰 곳

**테스트 *건너뛰는* 곳:**
- 단순 CRUD (save/find 단순 위임)
- DTO 매핑, getter/setter
- 프레임워크가 보장하는 것
- Controller의 단순 위임 (Service 호출만 하는 메서드)

### 근거
- 실무도 100% 커버리지 안 함 — 핵심에 집중하는 게 정석
- 면접에서 "어디에 *왜* 테스트했냐"에 답하는 게 커버리지 숫자보다 강함 (판단력 어필)
- 테스트 없는 곳에서 회귀 버그가 나면 추적 비용이 큼 → 핵심엔 반드시
- 8개월 한정 — 학습/개발 시간 효율

### 트레이드오프 / 한계
- 커버리지 숫자는 낮음 (의도적 — 숫자가 목표가 아님)
- 매번 "이거 테스트 대상인가?" 판단해야 함 → 작업 전 6단계 점검에 포함
- 핵심/단순 경계가 애매하면 → *일단 테스트하는 쪽* (안전 우선)

---

## ADR-022: 구분자 자동 감지 — "2조각 내는가" 검증 방식

**상태:** 채택 (2026-05-18)
**범위:** `ImportService`

### 컨텍스트
일괄 등록 시 사용자가 구분자(`separator`)를 직접 지정해야 함. 자동 감지하면 편함.
후보: 탭 `\t` / 파이프 `|` / 콜론 `:` / 콤마 `,` / 하이픈 `-`.
난점: 하이픈·콜론·콤마는 *단어 안에도* 등장 (`well-known`, `09:00`) → 진짜 구분자랑 헷갈림.

### 고려한 대안
- **A. 첫 줄 기준** — 첫 줄에서 우선순위대로 첫 발견. ❌ 첫 줄 특이하면 통째로 틀림
- **B. 빈도 기준** — 전체 최다 등장. ❌ 단어 내 하이픈이 최다라서 오감지
- **C. ✅ "2조각 내는가" 검증** — 후보로 split 했을 때 *정확히 2조각* 나는지
- **D. 자동 감지 포기** — 기본값 + 수동 지정만. SHOULD 기능 포기

### 결정
**C.** 후보를 우선순위대로(탭 → 파이프 → 콜론 → 콤마 → 하이픈) 보면서:
- 앞 N줄(5줄)을 그 구분자로 `split(..., 2)` → *2조각 나는 줄이 과반*이면 채택
- 아무것도 안 맞으면 기본값(하이픈)
- 사용자가 `separator`를 *명시*했으면 자동 감지 안 함 (명시 우선)
- **(보완 2026-05-18)** `ImportRequest.separator` 기본값(`" - "`) *제거* → null.
  안 그러면 기본값이 항상 들어가 *자동 감지가 발동 안 함*. 기본값 제거로 자동 감지가 *기본 동작*이 됨.

### 근거
- 진짜 구분자는 "front〈구분자〉back" → split하면 *딱 2조각*
- "2조각" 기준이라 단어 내 하이픈/콜론에 안 속음 (그건 3조각 이상 만들거나 안 가름)
- 우선순위는 *헷갈릴 위험 낮은 순* (탭·파이프는 단어에 거의 없음 → 먼저)
- 단순함 유지 — 후보 5개 × 앞 5줄

### 트레이드오프 / 한계
- 구분자 2종 혼용 입력은 못 잡음 → 그땐 사용자가 `separator` 수동 지정 (자동 감지는 *편의 기능*, 수동 옵션 유지)
- 표본 N=5줄 — 너무 적으면 부정확, 너무 많으면 느림. 5가 적당
- 모든 후보 실패 시 기본값(하이픈) — 그래도 틀리면 사용자가 수동 지정

---

## ADR-023: 일괄 등록 중복 카드 — Skip (front 기준)

**상태:** 채택 (2026-05-18)
**범위:** `ImportService`

### 컨텍스트
같은 단어장에 *이미 있는 카드*를 또 import하면? 현행 = 그냥 다 추가 → 같은 텍스트 두 번 올리면 `apple` 카드가 2개·3개 쌓임.

### "중복"의 정의
**front(앞면) 기준.** 같은 front면 중복.

### 고려한 대안
- **A. ✅ Skip** — 이미 있는 front는 무시, 기존 카드 유지. 응답에 `skipped` 개수 포함
- **B. Overwrite** — 이미 있는 front면 back을 새 값으로 교체
  - ❌ 사용자가 손수 고친 back을 import가 *조용히 덮음*
- **C. 중복 허용 (현행)** — ❌ 카드 폭증
- **D. 옵션 제공** — ❌ ImportRequest에 전략 필드 추가, 복잡

### 결정
**A. Skip + 응답에 `skipped` 카운트.** 같은 import 안의 중복도 막음 (먼저 등록된 것만).

### 근거
- 기존 데이터 보존 — overwrite의 "조용한 덮어쓰기"는 ADR-020의 *silent loss 방지* 정신에 어긋남
- 단순 — 옵션 안 늘림
- `skipped` 카운트 → 사용자가 *몇 개 건너뛰었는지* 인지 (silent 아님)

### 동음이의어 (front 같고 뜻 다름) — 문제 아님
front 기준 skip이라 같은 철자는 한 카드만 등록됨. 단 이건 *한계가 아니라*:
> 한 카드의 back에 여러 뜻을 쓰면 됨 — `bank - 은행, 둑` / `spring - 봄, 용수철, 샘`

Quizlet도 이 방식. 동음이의어 때문에 카드를 쪼갤 필요 없음.

### 트레이드오프 / 한계
- 기존 카드 back을 *일괄로 고치고 싶은* 경우 import로 불가 → 개별 `PATCH /cards/{id}`
- overwrite가 진짜 필요해지면 나중에 D(옵션)로 확장

---

## ADR-024: Quiz 세션 단위 관리 — Eager 생성 (시작 시 N문제 미리 확정)

**상태:** 채택 (2026-05-18)
**범위:** `QuizService`, 새 엔티티 `QuizSession` / `QuizQuestion`, V5 마이그레이션

### 컨텍스트
현재 Quiz는 한 문제씩 *독립 출제·제출* (QuizAttempt에 시도만 누적). 세션 개념 없음.
목표: "1회 퀴즈 = N문제 묶음" — 시작 → 진행 → 요약. 중복 출제 방지, 일관성, 결과 요약.

### 고려한 대안
- **A. ✅ Eager** — 시작 시 N문제 미리 생성·DB 저장, 사용자는 하나씩 풀이
- **B. Lazy** — 세션만, "다음 문제" 요청 시 생성 (중복 방지 어려움)
- **C. 최소 변경** — quiz_attempts에 session_id만 추가 (세션 상태 약함)
- **D. A + Pause/Resume/시간제한** — 과함 (지금 단계)

### 결정
**A. Eager 생성** + 부수 결정:
- **choices 저장 = JSON 컬럼** (별도 choices 테이블 X — 단순)
- **기존 `QuizAttempt` 유지** (호환, 점진 마이그레이션 — STRETCH로 deprecate 가능)
- **세션당 기본 N=10 문제** (Quizlet 표준)

### 근거
- *일관성* — 시작 후 카드 추가/삭제돼도 그 세션 N문제는 고정
- *중복 출제 방지* 자동 (미리 N개 골라놨으니 같은 카드 안 나옴)
- *진행도/요약* 자연스러움 (DB에 다 있음)
- 면접 답변: "세션 시작 시 문제 확정 → 풀이 중 카드 변동과 무관, 동시 진행도 안전"

### 데이터 구조 (V5 마이그레이션)

```
quiz_sessions
  id, user_id, deck_id, direction, total, started_at, ended_at

quiz_questions
  id, session_id, card_id,
  question_text, choices_json (JSON 배열), correct_answer,
  selected_answer (NULL=아직), is_correct (NULL=아직), answered_at (NULL=아직)
```

### 트레이드오프 / 한계
- N개 row 미리 저장 (학습 서비스 규모엔 부담 0)
- choices JSON은 쿼리/검증 제한 (단순함과의 트레이드 — 검색·필터 안 함)
- 카드 < 5개면 fallback 필요 (CHECKLIST에 명시 — 2~4지선다)

---

## ADR-025: 통합 테스트를 Testcontainers + MySQL로 전환 (H2 폐기)

**상태:** 채택 (2026-05-22)
**범위:** `src/test/**`, `build.gradle`, `application.yml` (test)

### 컨텍스트
ADR-024 구현 중 `quiz_questions.choices_json JSON` 컬럼이 H2에서 깨짐 (역직렬화 실패). 원인 — H2의 JSON 동작이 MySQL과 미묘하게 다름. 운영(MySQL)에선 안 깨질 코드인데 *H2 테스트가 운영을 못 따라옴*.

이 사건이 *원리적 위험*을 노출 — H2와 MySQL은 JSON / 함수 / 락 / 인덱스 / 트리거 동작이 다 다름. 옛 표준 패턴(H2 in-memory)은 *2024년 이후 사실상 안티패턴*.

### 고려한 대안
- **A. ✅ Testcontainers + MySQL** — 도커로 *진짜 MySQL 8 컨테이너* 띄워 테스트
- B. H2 유지 + MODE=MySQL 강화 — JSON 동작은 여전히 다름
- C. Mockito mock 확대 — DB 로직(트랜잭션/JPA 변경감지/FK) 검증 불가
- D. JSON 컬럼 → TEXT로 회피 — 즉시 해결되지만 *근본 원인* 안 잡힘. 다음 차례 막힘

### 결정
**A. Testcontainers 정식 도입.**
- 베이스 클래스 `AbstractIntegrationTest` — `@Container static MySQLContainer` (클래스 로딩 시 1회 시작)
- `@ServiceConnection` (Spring Boot 3.1+) — datasource 자동 주입
- 모든 통합 테스트 `extends AbstractIntegrationTest`
- `application.yml` (test) — datasource 제거. Flyway/JPA만 유지 (`ddl-auto: validate`)
- 컨테이너 reuse 옵션 (`~/.testcontainers.properties: testcontainers.reuse.enable=true`)으로 재시작 시간 흡수

### 근거
- *운영과 100% 동일 환경* — JSON, 함수, 락, FK, Flyway 마이그레이션까지 실제 검증
- 면접 답변: "H2 쓰다가 운영에서 깨짐 → 도커로 진짜 MySQL 띄워 *운영 동등* 테스트로 전환"
- 2024년 이후 *현업 표준* 트렌드와 정렬
- Phase 5 Redis 도입 시 Testcontainers 인프라 이미 있어 자연스럽게 확장

### 트레이드오프 / 한계
- 컨테이너 부팅 시간 (~10초 초회 / reuse 시 ~0초)
- Docker daemon 필수 — 사용자 PC 및 CI 환경에 도커 있어야
- 의존성 3개 추가 (`spring-boot-testcontainers`, `testcontainers:junit-jupiter`, `testcontainers:mysql`)
- **Testcontainers 1.21.3 강제** (Spring Boot 3.3 기본 1.19.8 오버라이드 — `build.gradle:14-16`). 사유: 사용자 PC Docker 29.4가 너무 신버전이라 1.19.8 호환성 미보장. 1.21.3이 Docker 29와 안전. Spring Boot 공식 검증 조합에서 벗어나는 *책임 감수*.
- **Windows Docker named pipe 명시 필요** — `~/.testcontainers.properties`에 `docker.host=npipe:////./pipe/docker_engine`. 자동 감지는 `dockerDesktopLinuxEngine`(잘못된 pipe) 잡아 실패. CI(Linux)는 자동 감지로 동작.
- **WSL Ubuntu 환경 권장** — Docker 29.4 + Windows named pipe 호환성 문제로 Windows 직접 실행보다 *WSL Ubuntu에서 unix socket* 경유가 안정. 셋업: `wsl --install -d Ubuntu` → Java 17 → `~/.docker-java.properties`에 `api.version=1.41` (Docker 29 info API 변경 우회).
- **`@ServiceConnection` 미사용 → `@DynamicPropertySource` 명시 주입** — `spring-boot-testcontainers` 3.3.0이 Testcontainers 1.19.x 의존인데 우리는 1.21.3 강제 → 모듈 충돌로 `@ServiceConnection`이 *조용히 작동 안 함*. `AbstractIntegrationTest`에서 `static { MYSQL.start(); }` + `@DynamicPropertySource` 명시 주입 패턴이 호환성 ↑.
- **`gradlew` 스크립트 Linux 호환 수정** — Spring Boot wrapper 옛 버전이 *Linux에서 `-Xmx64m`을 클래스명으로 넘김*. wrapper 한 줄 + CLASSPATH 위치 수정. Gradle wrapper 재생성 시 덮어쓰기 주의.
- **테스트 옵션** — `tasks.named('test') { failFast = true }`로 첫 fail 즉시 중단 (디버깅 시간 단축).

### 디버깅 교훈 (Phase 2 #4 마무리 시 누적)
1. *Profile yml은 main/test 양쪽 모두 확인 필수* — `src/main/resources/application-test.yml`이 옛 H2 셋업으로 남아 *test 모듈 갱신*을 덮어씀 (한 쪽만 보는 함정)
2. *Bulk Edit 시 한 파일이라도 빠지면 root cause 위장* — AuthServiceTest 1개만 `extends AbstractIntegrationTest` 누락 → 전체 39 테스트 fail로 보임. *grep으로 일괄 검증 필수*
3. *디버깅 시간 단축 = failFast + 짧은 진단 명령 (grep "Caused by")*이 1시간 → 2분 단축

---

## ADR-026: Typing 모드 — Quiz Eager 패턴 재사용 + 채점 정책 중간 길

**상태:** 채택 (2026-05-23)
**범위:** 새 `TypingService`, 새 엔티티 `TypingSession`/`TypingQuestion`, V6 마이그, `docs/typing-policy.md`

### 컨텍스트
사용자가 *답을 직접 타이핑*해서 제출하는 학습 모드. Quiz(4지선다)와 달리 open-ended → 채점 정책이 핵심.

### 결정 A — 세션 구조 = Quiz Eager 패턴 재사용
- **선택:** `typing_sessions` + `typing_questions` (시작 시 N문제 미리 생성, ADR-024와 동일 패턴)
- **대안:** Lazy (세션만, 즉석 출제) / Quiz 테이블 통합 (`type` 컬럼)
- **그럼에도 Eager:** Quiz와 *동일 패턴*이라 학습/운영 비용 0, 일관성 ↑. Lazy는 *중복 출제 방지* 추적 코드 또 짜야. *답 노출 위험은 Typing엔 없음*(선택지 X)이지만 일관성 위해 Eager. 단 `typing_questions`는 `choices_json` 없음

### 결정 B — 채점 정책 = 중간 길 (정규화 최소 + 쉼표 복수 정답)
- **선택:** `trim()` + `equalsIgnoreCase()` + **쉼표로 구분된 복수 정답 분리** (예: `"사과, 능금"` 저장 시 둘 중 하나 입력해도 정답)
- **대안:** (엄격) 정확 일치만 / (관대) Levenshtein 오타 허용 / (정규화 강화) 구두점/한글 자모 분리
- **그럼에도 중간:** 너무 엄격 → "Apple"≠"apple" → 사용자 화남 / 너무 관대 → 학습 의미 사라짐. *중간 + `docs/typing-policy.md`로 명시 문서화*. Levenshtein은 STRETCH (정확 암기에 위험)

### 부수 결정
- Quiz와 데이터 분리 (typing_questions에 `choices_json` 없음, attempt 구조 단순)
- V6 마이그
- `docs/typing-policy.md`로 채점 정책 별도 문서

### 트레이드오프 / 한계
- Eager 패턴 *2번 반복* (Quiz/Typing) → 향후 *공통 SessionService 추출* 리팩토링 여지
- 쉼표 분리는 "Hello, World" 같은 *답 자체에 쉼표 포함* 시 깨짐 (학습 서비스 규모엔 무관)
- 채점 정책 변경 시 *과거 attempt 결과*는 그대로 (재채점 안 함)

---

## ADR-027: Flashcard 모드 명확화 — 리네임 대신 javadoc + 문서

**상태:** 채택 (2026-05-23)
**범위:** `StudyService.java` (javadoc), 새 `docs/learning-modes.md`

### 컨텍스트
Phase 2 #6 항목: "기존 StudyService 흐름을 Flashcard 모드로 명확히 분리". 현재 `StudyService`가 *안다/모른다 기록 + 통계* = 사실상 *Flashcard 모드*인데 클래스명이 일반적이라 *Quiz/Typing*과의 구분이 코드만 봐선 불명확.

### 고려한 대안
- **A. ✅ 클래스명 유지 + javadoc 보강 + `docs/learning-modes.md` 신규** (3가지 학습 모드 비교 표 + 각 ADR 링크)
- B. `StudyService` → `FlashcardService` 리네임
- C. Flashcard/Quiz/Typing 공통 추상 (`AbstractSessionService`) 추출 — 큰 리팩토링

### 결정
**A.** javadoc + 별도 문서로 명확화.

### 근거
- B는 *API 경로 (`/decks/{deckId}/study/...`) / 테스트 / DB 테이블 (`study_sessions`, `study_records`) / 기존 커밋 히스토리*까지 다 변경 필요 → 회귀 위험 ↑, 작업 비용 ↑
- C는 *진짜로 추상이 필요해지는 시점* (모드 4개+ 또는 공통 흐름이 자연스럽게 추출됨)에 도입하는 게 정공. 지금은 *각 모드 흐름이 충분히 다름* (Flashcard=안다/모른다, Quiz=4지선다, Typing=open-ended) → *premature abstraction* 회피
- A는 *코드 거의 안 건드림* (javadoc만) → 회귀 위험 0, 면접 답변용 *문서 자산*만 추가

### 트레이드오프 / 한계
- *코드만 보고* `StudyService = Flashcard`임을 알려면 javadoc 봐야 함 (이름만으론 여전히 모호)
- 새 모드 추가될수록 *공통 패턴 추출* 압력 ↑ → 그 시점에 C로 전환 (트리거: 모드 4개+ 또는 *3 모드 사이 동일 흐름 발견*)

### `docs/learning-modes.md` 구성
3가지 학습 모드 (Flashcard / Quiz / Typing) 한 표로 비교:
- 어떤 클래스/엔티티/API 경로
- 어떤 ADR 결정
- 채점/판정 방식
- 사용처

---

## ADR-028: 통합 오답노트 — Aggregator 패턴 (3 모드 통합 + 최근 N일)

**상태:** 채택 (2026-05-23)
**범위:** 새 패키지 `com.vocamaster.wrongnote` (Service/Controller/DTO), 기존 Repository 3개에 시간 필터 메서드 추가, `TypingService.startSession`에 `wrongOnly` 분기

### 컨텍스트
Phase 2 #7 항목: 오답노트. 현재 *Quiz 단발 오답*만 `QuizService.getWrongCards`로 조회 가능. Typing/Flashcard 오답은 *DB엔 있지만 모아 보는 API 없음*. 사용자가 "내가 틀린 카드 한 번에 보여줘"를 원하는 자연스러운 학습 흐름.

### 고려한 대안
- **A. ✅ Aggregator 패턴** — 신규 `WrongNoteService`가 3 Repository를 병렬 호출 → 카드 ID 중복 제거 후 합쳐 응답. `GET /decks/{deckId}/wrong-notes?days=30`
- B. *API 3개 분리* — `quiz/typing/study` 각각 wrong API, 클라이언트가 합침
- C. *전용 테이블* — `wrong_card_ids(user_id, card_id, mode, created_at)` 신규. 오답 발생 시 트리거로 누적

### 결정
**A. Aggregator.**
- 응답 형식: `{ quiz: [...], typing: [...], flashcard: [...], combined: [...], total: N }` (모드별 + 통합 둘 다)
- 시간 필터: `?days=N` 쿼리 파라미터, 기본 30일 (없으면 전체)
- 중복 제거: `Set<Long>`으로 카드 ID 기준

### 근거
- B의 단점: *클라이언트 3번 호출* + *합치기 로직 클라이언트 책임* + *3 모드 인지 강제* → API 사용성 ↓
- C의 단점: *데이터 중복 저장* (원천이 3 테이블에 이미 있음) + *동기화 트리거* (오답 발생/취소 시) + *premature optimization* — 학습 서비스 규모엔 *서버 합산이 충분히 빠름*
- A의 장점: 기존 Repository만 활용 (변경 최소), Phase 3 Leitner Box의 *복습 우선순위* 입력으로도 자연스러움 (같은 통합 결과 재사용)

### 부수 결정
- **Typing `wrongOnly` 옵션**: `StartTypingSessionRequest.wrongOnly` 추가 (Quiz 패턴 일치, "오답만 재타이핑" 학습 흐름)
- **응답 분리**: 모드별 리스트 + 통합 리스트 *둘 다* 제공 — 클라이언트가 *모드별 본 후 통합 다시 본다*는 자연 UX

### 트레이드오프 / 한계
- 카드 1장이 *3 모드 모두 틀림* → combined에 1번만 노출 (정확). 단 *모드별 빈도* 정보는 없음 (필요해지면 응답 확장)
- *시간 필터 기본 30일* — 평생 누적 오답을 한 번에 보고 싶으면 `?days=0` (또는 큰 값) 필요. 기본값은 UX 우선
- *Eventually* 트래픽 폭증 시 C로 전환 트리거 — *오답 카드 한 번 조회에 100ms 초과* 시 검토

---

## ADR-029: 간격 반복 알고리즘 — Leitner Box (SM-2/FSRS 대신)

**상태:** 채택 (2026-06-24)
**범위:** 새 패키지 `com.vocamaster.review` (CardProgress 엔티티/Repository, ReviewService, Controller, DTO), `V7__add_card_progress.sql`, `docs/review-algorithm.md`

### 컨텍스트
Phase 3 핵심이자 VocaMaster의 *면접 메인 무기*. 망각곡선(에빙하우스)을 구현해 "자주 틀리는 단어는 자주, 잘 외운 단어는 가끔" 보여주는 간격 반복(Spaced Repetition)이 필요. 어떤 알고리즘으로 박스/간격을 계산할 것인가?

### 고려한 대안
- **A. ✅ Leitner Box** — 박스 6개. 맞으면 다음 박스로 승급(간격↑), 틀리면 **box 1로 리셋**. 박스별 *고정 간격* (10분/1일/3일/7일/14일/30일)
- **B. SM-2 (Anki 고전)** — 카드마다 *ease factor* + interval 계산, 답을 품질 0~5점으로 평가해 다음 간격 산출
- **C. FSRS (최신)** — 기억을 *Stability(안정성)·Retrievability(검색성)* 2변수로 모델링, 17개쯤 파라미터를 ML로 사용자 복습 기록에 맞춰 최적화

### 결정
**A. Leitner Box (6박스).**
- box 1~6 간격: `10분 / 1일 / 3일 / 7일 / 14일 / 30일`
- 맞힘: `box+1` (최대 6 고정), `next_review_at = now + 박스간격`
- 틀림: `box=1` 리셋, 짧은 간격(10분)으로 재등장
- `@Version` 낙관적 락으로 동시 답변 충돌 대비

### 근거
- 목표는 *왕초보가 8개월 안에 직접 짜고 면접에서 설명*하는 것 → **통제 가능한 복잡도**가 최우선. Leitner는 if/else 수준인데 망각곡선 핵심은 다 담음
- SM-2/FSRS는 더 정교하지만 *직접 설명 못 하면 의미 없음* (= NewsPick 안티패턴 반복)
- **FSRS는 cold start** — ML이 학습할 *복습 기록 데이터*가 신규 서비스엔 0
- "단순함이 목표"가 아니라 *통제·설명 가능한 선*을 고른 것. 더 정교한 건 **데이터 쌓이면 개선안**으로 (면접 가산점)

### 트레이드오프 / 한계
- *고정 간격*이라 개인별 망각 속도를 반영 못 함 (FSRS는 함) — 데이터 쌓이면 전환 여지
- 박스 간격(10분~30일)은 *경험칙*이지 논문 최적값 아님 — 운영하며 조정 가능 (상수 한 곳 관리)
- 틀리면 *box 1 완전 리셋* = 변형 Leitner(한 칸만 강등)보다 보수적. 정확 암기 우선이라 의도적 선택
- 동시 답변 시 `@Version` 충돌 → 409 또는 1회 재시도 정책 필요 (Phase 3 항목)

---

## ADR-030: Deck 공개 범위 — `visibility` enum 3값 (boolean/공유 토큰 대신)

**상태:** 채택 (2026-08-05)
**범위:** `DeckVisibility` enum, `Deck.visibility` 필드, `V9__add_deck_visibility.sql`, `PATCH /decks/{id}/visibility` — Phase 4 첫 결정

### 컨텍스트
Phase 4 = 공개 단어장/공유. 덱마다 "누가 볼 수 있는가" 상태 저장이 필요한데, 요구 상태가 2개가 아니라 **3개**: 나만(PRIVATE) / 검색 노출(PUBLIC) / 검색 비노출·링크로만 접근(UNLISTED — 유튜브 '일부 공개'). 이걸 어떻게 모델링할 것인가?

### 고려한 대안
- **A. ❌ `is_public` boolean** — 제일 단순하지만 칸이 2개뿐. UNLISTED 표현 불가. 나중에 확장하려면 4단계 공사 (새 컬럼 추가 → 데이터 이관 → 코드 전면 교체 → 구 컬럼 제거)
- **B. ✅ `visibility` enum 3값** — `PRIVATE`/`PUBLIC`/`UNLISTED`, `@Enumerated(EnumType.STRING)` 문자열 저장
- **C. ❌ 공유 토큰 테이블 분리** — `share_links(token, expires_at)`로 UNLISTED 구현. 링크 만료/회수 가능(구글 드라이브식)이지만 테이블 + 발급/검증/회수 API가 통째로 추가 — Phase 4 본체(검색→복사→좋아요) 앞에서 과설계

### 결정
**B. enum 3값 + `EnumType.STRING`.**
- DB: `VARCHAR(20) NOT NULL DEFAULT 'PRIVATE'` — 기존 행 전부 PRIVATE으로 채움
- **ORDINAL(숫자 저장) 금지** — enum 선언 순서가 바뀌는 순간 기존 데이터가 통째로 다른 의미로 읽히는 무소음 오염 사고 (에러도 안 남)

### 근거
- 상태 3개를 표현하는 **최소 설계**. String 컬럼 대비 오타를 컴파일 타임에 차단
- 기본값 PRIVATE = "의심스러우면 잠근다" — 업데이트 한 번에 기존 비공개 덱이 공개되는 사고 방지
- C(토큰)는 B와 배타적이지 않음 — 필요해지면 나중에 **추가**로 확장 가능. 지금 결정이 미래를 안 막음

### 트레이드오프 / 한계
- UNLISTED 링크는 회수 불가 (URL이 곧 접근권 — 퍼지면 PRIVATE 전환 외엔 수단 없음). 토큰 방식(C)이면 가능 — 필요 시 확장 항목
- UNLISTED는 **검색 비노출일 뿐, 비밀 링크 보안이 아님** (Codex 검산) — id가 순차 숫자라 `/public/decks/1,2,3…` 열거로 발견될 수 있음. 엄밀한 "링크 아는 사람만"은 UUID/랜덤 토큰 필요 → C안 확장으로 해결. 면접 멘트: "검색 비노출이며 URL 직접 접근은 허용하지만, 비밀 링크 수준의 보안은 아닙니다"
- 문자열 저장이 숫자보다 공간 큼 (무시 가능 수준)
- 값 추가는 자유롭지만 값 *이름 변경*은 데이터 이관 필요 (STRING 저장의 대가)

---

## ADR-031: 덱 복사 — copy_count 원자적 UPDATE + 접근/카운트 정책

**상태:** 채택 (2026-08-10)
**범위:** `POST /decks/{deckId}/copy`, `V10__add_deck_copy_tracking.sql` (copy_count, original_deck_id), `DeckService.copy`, `DeckRepository.incrementCopyCount`

### 컨텍스트
공개 덱을 내 덱으로 복사하는 Phase 4 핵심 기능. 두 갈래 결정이 필요: ① 인기 덱에 동시 복사가 몰릴 때 copy_count를 어떻게 정확히 올리나 ② 누가 무엇을 복사할 수 있고, 어떤 복사가 카운트에 반영되나.

### 고려한 대안 — 카운터
- **A. ❌ read-modify-write** — 자바에서 읽고 +1 후 저장. 동시 2건이 둘 다 5를 읽고 둘 다 6을 저장 → 증가 1개 증발(lost update). 에러 없이 조용히 틀리는 최악 유형
- **B. ❌ `@Version` 낙관적 락** — 리뷰(ADR-029)에서 쓴 도구. 거기선 충돌이 *드문 이상 상황*이라 "낡은 쪽 409 거부"가 정답이었지만, 카운터는 **충돌이 정상 상황**이고 양쪽 다 반영돼야 함. 인기 덱일수록 재시도 폭풍
- **C. ✅ DB 원자적 UPDATE** — `set copyCount = copyCount + 1`. 더하기를 DB 안에서 수행, 같은 행 갱신은 행 잠금이 줄 세움 → 5→6→7 둘 다 생존
- **D. ❌(지금은) 비동기 이벤트 집계** — 초대규모용. Phase 6(이벤트)에서 재론

### 결정 — 정책 (2026-08-10 사용자 확정, Codex가 모순 지적)
- 남의 덱: PUBLIC/UNLISTED 복사 가능, **PRIVATE은 404** (공개 조회와 동일한 존재 숨김)
- **자기 덱: visibility 무관 복사 가능** — "PRIVATE 404" 규칙과의 모순(내 PRIVATE 덱 템플릿 복사 불가)을 Codex가 지적 → `isOwner` 분기로 해소 (남에게만 404)
- **자기 복사는 copy_count 증가 제외** — copy_count는 이후 인기 점수 재료. 자기 복사 반복으로 순위 조작 가능하므로 카운트에서 배제 ("조작하면 의미가 없다" — 사용자 결정)
- 복사본: 무조건 PRIVATE, `original_deck_id`로 출처 추적 (자기참조 FK + `ON DELETE SET NULL` — 원본 삭제돼도 복사본 생존)
- 카드: **콘텐츠 복사**(front/back/exampleSentence/memo/position — memo는 개인 메모가 아니라 공유 설명 성향이므로 콘텐츠로 분류, 사용자 결정) / **학습 상태 리셋**(starred=false, CardProgress 미복사)
- 카드 0개 덱: 복사 허용 (빈 덱도 정상 상태)
- 복사 중 원본이 PRIVATE 전환: **복사 트랜잭션이 원본 visibility를 처음 읽은 시점 기준** (MySQL REPEATABLE READ의 일관 읽기 스냅샷은 첫 조회 때 생성)

### 근거
- 데이터 성격에 맞는 동시성 도구 선택이 핵심: 학습 상태(한쪽 거부가 정답) → `@Version`, 카운터(양쪽 반영이 정답) → 원자적 UPDATE
- 전체를 하나의 `@Transactional`로 — 카드 500장 중 300장째 실패 시 "완전한 복사본 또는 아무것도 없음" (Import P1-6과 동일 원리)

### 트레이드오프 / 한계
- `@Modifying` 벌크 UPDATE는 1차 캐시를 우회 → `flushAutomatically/clearAutomatically` 필수 (알려진 함정 목록)
- 자기 복사 카운트 제외는 단순 분기 — 다계정 조작까지는 못 막음 (레이트리밋/어뷰징 방어는 범위 밖)
- copy_count와 실제 복사본 수의 불일치 복구는 좋아요 섹션의 STRETCH 스케줄러와 동일 계열로 미룸

---

## ADR-032: 좋아요 — 멱등성은 복합 unique 제약이 보증, 자기 좋아요 허용

**상태:** 채택 (2026-08-10)
**범위:** `deck_likes` 테이블(`V11`), `POST/DELETE /public/decks/{id}/like`, `Deck.like_count` 동기화, SecurityConfig permitAll을 `GET /public/**`로 축소

### 컨텍스트
좋아요는 더블탭·재시도·중복 클릭이 일상인 API — 같은 요청이 몇 번 와도 결과가 같아야(멱등) 하고, like_count는 인기 점수(가중치 ×5) 재료라 정확해야 함. 또한 체크리스트 경로가 `/public/**` 하위인데 기존 permitAll이 전체 메서드를 열고 있어 익명 쓰기가 뚫리는 충돌 발견.

### 고려한 대안 — 멱등성 보증
- **A. ❌ 애플리케이션 exists 체크만** — check와 INSERT *사이*에 끼어드는 레이스에 항상 뚫림
- **B. ✅ exists 빠른 경로 + INSERT + (user_id, deck_id) 복합 unique** — 위반 시 "이미 좋아요"로 멱등 응답. 물리적 보증은 DB 제약, 코드 체크는 흔한 경우의 빠른 반환
- **C. ❌ `INSERT IGNORE` / `ON DUPLICATE KEY`** — 동작하나 MySQL 방언 + JPA 이탈, 지금 이득 없음

### 결정
- **카운트는 원자적 UPDATE + X락 먼저** — deck_likes INSERT의 FK S락 + 카운트 X락은 ADR-031 복사 데드락과 동일 교착 구조. 같은 예방책(잠금 획득 순서 통일) 적용
- 레이스로 unique 위반 시 **트랜잭션 전체 롤백(선행 증가 포함)** → 컨트롤러가 현재 상태를 재조회해 멱등 응답 (트랜잭션 *안*에서 catch하면 rollback-only 마킹과 충돌하는 함정 — catch는 프록시 밖에서)
- unlike: `deleteBy...`가 반환한 **지운 행 수 > 0일 때만 감소** — 자연 멱등, 카운트 음수 경로 없음
- **자기 좋아요 허용** (2026-08-10 사용자 결정, 타 서비스 관례): 자기 복사(무한 반복 가능 → 카운트 제외)와 달리, **unique 제약이 1인 1회 상한을 물리적으로 강제** → 자기 좋아요의 조작 여지는 +1로 캡. 카운트 포함해도 무해
- 접근 규칙은 복사와 동일: 남의 PRIVATE = 404 존재 숨김, 자기 덱은 visibility 무관
- SecurityConfig: `/public/**` permitAll을 **GET 한정**으로 축소 — 쓰기(좋아요)는 인증 필수

### 트레이드오프 / 한계
- 같은 유저의 like·unlike 동시 레이스가 만들 수 있는 이론적 교착은 MySQL 감지(한쪽 강제 롤백)에 맡김 — 클라이언트 재시도로 수렴
- like_count와 deck_likes 실제 수의 드리프트 복구는 STRETCH 스케줄러 항목으로 미룸
- 복합 unique는 "한 계정 1회"까지만 — 다계정 어뷰징은 범위 밖 (ADR-031과 동일)

### 보강 (2026-08-10, Codex 검산)
- **V12: deck FK에 `ON DELETE CASCADE`** — 좋아요 달린 덱 삭제가 FK 위반 500 나던 구멍 수리. deck_likes는 Deck에 역방향 컬렉션을 안 만든 설계라 JPA cascade가 못 지움 → DB에 위임. 회귀 테스트 포함
- **user FK는 RESTRICT 유지** — 유저 CASCADE는 좋아요 행만 지우고 덱들의 like_count를 안 줄여 무소음 드리프트 유발. 유저는 소프트 삭제(deleted_at) 정책이라 하드 삭제 경로 없음

---

## ADR-033: 인기 정렬 — DB 계산식 정렬, 공식에서 study 항 제외

**상태:** 채택 (2026-08-11)
**범위:** `GET /public/decks?sort=popular|recent`, `DeckRepository.searchByVisibilityPopular`, `PublicDeckResponse`에 likeCount/copyCount 노출

### 컨텍스트
Phase 4 완료 기준 데모("인기 목록 반영 시연")에 필요한 마지막 조각. 체크리스트 원안 공식은 `like×5 + copy×3 + study×1`이었으나, 구현 직전 검산(Codex)에서 study 항의 의미 결함 발견: 다른 유저는 공개 덱을 **복사한 자기 덱으로** 학습하므로, 원본의 study_count는 사실상 "원작자가 자기 덱을 연 횟수"가 됨. 세션 시작은 무한 반복 가능 → 자기 좋아요(1회 캡)와 달리 무제한 조작 통로.

### 고려한 대안
- **A. ❌ 원안 유지 (study 포함)** — 3모드(Flashcard/Quiz/Typing) 백필 + 증가 3곳 + StudyService `@Transactional` 추가 필요. 정의를 문서화해도 "커뮤니티 인기"라는 이름과 "원작자 자기 활동"이라는 실데이터가 어긋남
- **B. ❌ 원본 귀속 설계** — 복사본 학습 시 `original_deck_id` 따라 원본 카운트 증가. 의미는 가장 정확하나 인기 덱 행에 락 집중 + 설계·테스트가 현 범위 초과
- **C. ✅ study 항 제외** — `like×5 + copy×3`. 잘못 정의된 숫자를 만들지 않음. study는 **Phase 6 이벤트 설계에서 원본 귀속과 함께** 재도입 (사용자 결정 2026-08-11, Codex 동일 추천)

### 결정
- popular: `order by (like_count*5 + copy_count*3) desc, created_at desc` (동점은 최신순) — JPQL 계산식 정렬
- recent: 기존 `created_at desc` 유지 (기본값). 그 외 sort 값은 400
- 조작 방지 계열 정리: 자기 복사=카운트 제외(무한 반복 가능) / 자기 좋아요=허용(unique 1회 캡) / **자기 학습=공식 진입 자체를 보류(무한 반복 + 귀속 왜곡)**

### 트레이드오프 / 한계
- 계산식 정렬은 인덱스를 못 탐 → 후보 전체 filesort. 현 규모 무해, 덱 수만 개부터 병목 — **Phase 5 Redis 랭킹(ZSET) 전환의 근거** (면접: "지금 왜 충분한가 + 언제 무엇으로 바꾸나")
- 가중치 5:3은 경험칙 — 운영 데이터로 조정 여지 (Leitner 간격과 같은 성격)

---

## ADR-034: 로그인 Rate Limit — Redis 고정 창 + email 기준 + fail-open

**상태:** 채택 (2026-08-12)
**범위:** `LoginAttemptService`, `AuthService.login` 훅, `TooManyRequestsException`(429 + Retry-After), `ratelimit.login.enabled` 스위치 — Phase 5 첫 사용처

### 컨텍스트
무차별 대입(brute force) 방어가 없음. Phase 5의 첫 Redis 사용처로 이걸 고른 이유: **캐시가 아니라서** — 랭킹·요약 캐시는 "원본은 DB, Redis는 사본"이라 캐시 무효화 문제가 붙지만, 실패 카운터는 Redis가 그 데이터의 **유일한 주인**이라 TTL과 원자적 INCR만 이해하면 되는 입문 사례.

### 고려한 대안 — 저장소
- **A. ❌ 자바 인메모리(HashMap/Caffeine)** — Redis보다 빠르지만 **서버 2대면 카운터가 쪼개져** 5회 제한이 사실상 10회가 됨. 배포 재시작마다 초기화 = 공격자가 리셋 획득
- **B. ❌ DB 테이블** — 매 로그인 실패마다 write + 만료 행 청소 배치 필요. 인증 경로에 쓰기 부하를 얹는 구조
- **C. ✅ Redis** — 여러 서버가 보는 **공유 카운터** + TTL 자동 만료 + `INCR` 원자성. 셋 다 필요한 요구사항이고 Redis는 셋을 기본 제공

### 고려한 대안 — 카운트 기준
- IP만: 공유 IP(회사/학교 NAT)에서 무고한 사용자 연쇄 차단 + 분산 IP 공격에 무력
- email+IP 조합: 조합이 다르면 별개 카운터라 IP 순회로 우회 가능
- **✅ email 기준**: 방어 목표가 "이 계정의 비밀번호 뚫기"이므로 계정이 방어 단위

### 결정
- **5분 내 5회 실패 → 30분 잠금 → 429 + `Retry-After` 헤더** (고정 창)
- **이메일 존재 여부와 무관하게 카운트** — 있는 계정만 세면 `401 vs 429` 차이가 곧 회원 명단 누설(user enumeration). Phase 4의 404 존재숨김과 같은 원칙
- **TTL은 첫 증가에서만** — 매 실패마다 갱신하면 창이 계속 밀려 영원히 안 풀림
- 이메일은 **소문자 정규화** — 대소문자만 바꿔 카운터를 갈아타는 우회 차단
- **fail-open**: Redis 예외(`DataAccessException`)는 삼키고 통과 + 경고 로그. 연결/명령 타임아웃 300ms로 장애가 로그인 지연으로 번지지 않게
- 로그인 성공 시 카운터·잠금 삭제
- `ratelimit.login.enabled` 스위치: 운영 킬 스위치 겸, **테스트 기본 off** (실패 로그인을 반복하는 기존 테스트가 공용 Redis에 잠금을 남겨 다른 테스트·데모 계정을 잠그는 오염 방지)

### 근거
- Redis 쓰기는 JPA 트랜잭션 밖이라 **401 롤백에도 실패 기록이 살아남음** — P1-1(제재가 롤백에 증발)에서 물렸던 성질이 여기서는 정확히 우리 편
- 429/`Retry-After`는 401과 다른 계약: "자격 증명이 틀렸다"가 아니라 "지금은 시도 자체가 무의미하고 N초 뒤 풀린다"를 기계가 읽게 함

### 트레이드오프 / 한계
- **lockout DoS**: 남의 이메일로 일부러 5번 틀려 30분 잠글 수 있음. 완화 = 잠금 30분 상한 + 성공 시 즉시 리셋. 근본 해결(디바이스 신뢰/CAPTCHA)은 범위 밖 — **알려진 한계로 수용**
- **fail-open의 대가**: Redis를 죽이면 무차별 대입 방어가 풀림. 인증 서버 가용성을 우선한 의도적 선택이며 Phase 7 관측에서 **Redis 다운 알림**으로 보완 (fail-open 테스트가 이 사실을 박제)
- **고정 창 경계 효과**: 창 경계에 걸치면 짧은 시간에 최대 2배(≈10회) 허용. 슬라이딩 윈도우(Sorted Set)로 정밀화 가능하나 복잡도 대비 이득이 낮아 보류
- IP 차원 제한 없음 — 계정 순회 공격(계정마다 4회씩)은 못 막음. Phase 7 후보

---

## ADR-035: 인기 랭킹 — Redis ZSET, 캐시는 순서만·권한은 DB

**상태:** 채택 (2026-08-12)
**범위:** `DeckRankingService`, `PublicDeckService` 캐시 경로, 도메인 훅 4곳(좋아요·복사·공개전환·삭제), `docs/cache-strategy.md`, `ranking.popular.enabled` 스위치

### 컨텍스트
`sort=popular`의 계산식 정렬은 인덱스를 못 타는 filesort (ADR-033에서 예고한 병목). 인기 목록은 비로그인 사용자도 반복 조회하는 hot path라 읽기 부하가 주 DB에 직접 꽂힌다.

### 고려한 대안
- **A. ❌ MySQL 생성 컬럼 + 인덱스** — `popularity AS (like_count*5+copy_count*3) STORED` + 인덱스로 filesort 제거. **현 규모에선 이게 더 단순하다.** 탈락 이유: 읽기 트래픽이 여전히 주 DB를 침, 일간 급상승 등 확장 시 매 요청 집계 필요, Phase 5의 학습 목표(캐시 무효화를 안전한 자리에서 겪기)와 불일치. **알고도 Redis를 선택했음을 명시** — "인덱스로 되잖아요?"의 답
- **B. ❌ ZSET에 덱 내용까지 통째 캐싱** — 조회 1번으로 끝나지만 제목·닉네임·visibility 변경마다 완벽 동기화 필요. 갱신 누락 한 곳 = 낡은 정보 노출 한 곳, visibility면 **보안 사고**
- **C. ✅ ZSET에 id·점수만 + DB 재검증** — 캐시가 낡아도 최악이 "목록이 성긴 것". 비공개 노출은 구조적으로 불가능

### 결정
- `popular:decks` (ZSET, 65분) + `popular:decks:ready` (표지, 60분) — **TTL 시차**로 만료 직후 증감이 가짜 순위표(덱 하나·무TTL)를 만드는 레이스를 구조 차단 (Codex 검산)
- 갱신은 **afterCommit에서만** — 더블탭 롤백 등에서 DB는 원복됐는데 Redis 점수만 남는 드리프트 방지. rate limit에선 "트랜잭션 밖" 성질이 약이었지만 여기선 독 — 같은 성질의 양면
- afterCommit 안의 Redis 예외는 삼킴 — 커밋 성공 후 500은 사용자 재시도 → 중복 유발
- 캐시 경로는 `sort=popular` + keyword 없음일 때만. stale id 발견 시 ZREM(자가 치유) + 그 요청은 DB 폴백. totalElements는 DB count (ZCARD는 stale 포함 가능)
- `updateVisibility`/`remove`에 `@Transactional` 신설 — afterCommit 등록 지점 확보 (Codex 검산)

### 트레이드오프 / 한계
- 최종적 일관성 — 순위가 최대 1시간 낡을 수 있음 (안전망 TTL). 정확성이 필요한 건 전부 DB에 있음
- 재구축 동시 실행은 last-wins (멱등이라 무해, 분산 락은 과설계)
- 캐시 경로도 DB를 침 (재검증 PK 조회 + count) — 없앤 건 정렬 비용이지 DB 접근 전부가 아님

---

## ADR-036: 오늘 복습 요약 — cache-aside + 단일 관문 무효화

**상태:** 채택 (2026-08-12)
**범위:** `TodaySummaryCache`, `ReviewService.getTodaySummary` cache-aside 전환, `StatsService.recordStudy` 무효화 훅(+조기 return 구조 수리), `TodaySummaryResponse` 역직렬화 가능화, RedisConfig FIELD 접근 — Phase 5 세 번째(마지막) 사용처

### 컨텍스트
현황판(숫자 4개)이 조회마다 집계 쿼리 4방. 같은 사용자가 학습 화면을 오가며 반복 호출 — 개인별 데이터라 랭킹처럼 미리 만들 수 없어 **조회 시점에 채우는 cache-aside**가 정석.

### 결정
- 키 `review:summary:{userId}:{yyyyMMdd}` (KST) + **TTL 5분** — 날짜가 키에 있어 자정 넘으면 어제 캐시가 구조적으로 무효
- **역할 분담이 이 ADR의 핵심**: 무효화(evict)는 사용자의 *행동*(학습)이 만든 변화를, **TTL은 행동 없이 시간만 흘러 변하는 dueCount**(nextReviewAt 경과 — 이벤트가 없는 변화)를 잡는다
- 무효화는 **recordStudy 단일 관문** — 4개 학습 모드(Review/Quiz/Typing/Study) 전부가 이미 통과하는 자리. ⚠️ 기존 `updated==1` 조기 return을 구조 변경(`updated==0` 분기) — 그대로 끝에 붙였으면 오늘 두 번째 학습부터(최다 경로) 무효화가 실행되지 않는 조용한 버그 (Codex 검산)
- evict는 **afterCommit** (ADR-035와 동일 근거 — 커밋 전 삭제는 낡은 값 재캐싱 레이스)
- 무효화 = 갱신이 아니라 **삭제** — 재계산은 다음 조회가. 숫자 4개를 부분 수정하는 것보다 단순·안전
- DTO 왕복: `@NoArgsConstructor(force) + @AllArgsConstructor + @Builder` 트리오 + 캐시 매퍼 FIELD 접근 — "캐시에 넣는 값은 왕복 가능해야 한다"(List.of 교훈)의 DTO판
- 캐시 get의 catch는 **의도적으로 RuntimeException 광범위** — 연결 실패뿐 아니라 손상 값의 역직렬화 실패·타입 불일치까지 전부 '미스'로. 좁히면 손상 캐시가 500으로 샘 (손상 재현 테스트 포함)
- 조회는 `now/today`를 한 번만 뽑아 캐시 get·계산·put이 같은 날짜 사용 — 자정 경계에서 23:59 계산 결과가 다음날 키에 저장되는 어긋남 방지 (Codex 검산)

### 트레이드오프 / 한계
- **stats→review 의존이 생김** (출석 담당이 요약 캐시를 앎) — 책임 섞임을 알고 수용. Phase 6에서 "학습했음" 이벤트 발행/구독으로 분리하는 것이 정답 (예고편)
- afterCommit 극단 타이밍의 낡은 재캐싱은 최대 5분 허용 — 현황판은 잔액이 아님
- 자정 경계 동작은 코드로 보장하나 테스트 불가 (Clock 미주입 — STRETCH 항목과 연동)

---

## ADR-037: 도메인 이벤트 도입 — 학습 기록이 구독자를 모르게 (Phase 6 첫 걸음)

**상태:** 채택 (2026-08-19)
**범위:** `StudyRecordedEvent`(record), `StatsService.recordStudy`의 발행, `TodaySummaryCacheListener`(`@TransactionalEventListener(AFTER_COMMIT)`), `TodaySummaryCache.evictAfterCommit → evict` 단순화

**배경 — ADR-036에서 승인했던 냄새:**
출석 담당 `StatsService`가 복습 요약 캐시 `TodaySummaryCache`를 직접 호출했다. "모든 학습 모드의 단일 관문이라 여기서 지우는 게 제일 싸다"는 판단은 맞았지만, 학습 후에 해야 할 일이 늘면(인기 점수 study 항, 배지, 미션) recordStudy가 그 전부를 직접 알게 되는 구조 — 한 메서드가 5개 부서를 호출하는 미래.

**결정:** recordStudy는 `StudyRecordedEvent(userId, date)`를 **발행만** 한다. 관심 있는 쪽이 각자 구독.
- 구독자 실행 시점은 **`AFTER_COMMIT`** — Phase 5에서 손으로 짰던 `TransactionSynchronization.afterCommit`(ADR-035/036)의 스프링 표준판. 의미 동일: 커밋 전에 캐시를 지우면 그 빈틈에 다른 요청이 커밋 전 옛 값을 읽어 재캐싱한다. 롤백이면 리스너가 아예 안 불린다(지울 이유가 없으니 맞는 동작 — 테스트 `rollback_doesNotEvict`로 박제).
- 지금은 **동기** 리스너(커밋한 스레드가 이어서 실행). `@Async`는 리스너가 무거워질 때(외부 호출·집계) — 그때 "실패가 조용히 묻히지 않게" 로깅 정책과 세트로.

**대안:**
- (a) 현상 유지(직접 호출) — 지금은 1줄이라 제일 단순. 그러나 구독자가 늘 때마다 recordStudy 수정 = 출석 코드가 남의 사정으로 바뀜
- (b) `@EventListener`(즉시 실행) — 트랜잭션 안에서 돌아 ADR-036의 레이스가 부활
- (c) 바로 Kafka — 프로세스 밖 브로커는 지금 문제(같은 프로세스 안 결합)에 과한 도구. Spring Event로 먼저 경계를 그리고, 필요가 증명되면 리스너 하나씩 교체(체크리스트 STRETCH)

**트레이드오프 / 알려진 한계:**
- 흐름이 코드에서 안 보인다 — "recordStudy 다음에 뭐가 일어나지?"를 알려면 이벤트 타입으로 리스너를 검색해야 함. 이벤트 클래스 javadoc에 발행처·구독처를 적어 상쇄
- AFTER_COMMIT 리스너 안에서 쓰기 트랜잭션은 기본적으로 새로 시작되지 않는다(`REQUIRES_NEW` 필요) — 지금은 Redis 삭제뿐이라 해당 없음, 다음 리스너(랭킹 study 항)에서 주의
- 동기 리스너의 예외는 발행자에게 전파되지 않고 로그로 끝남(스프링 기본) — fail-open과 같은 성질, 단 "모르고 지나감" 위험 → Async 전환 시 정책 명시

---

## ADR-038: 인기 점수 study 항 재도입 — 원본 귀속 + 하루 1회 + 자기 학습 제외

**상태:** 채택 (2026-08-22)
**범위:** V13(`decks.study_count`, `deck_study_days` unique 출석부, original_deck_id 평탄화), `DeckStudyRankingListener`(AFTER_COMMIT + REQUIRES_NEW), `StudyRecordedEvent.deckId`, `DeckService.copy` 평탄화, 가중치 like 5 / copy 3 / **study 1** (JPQL·rebuild·증분 3곳)

**배경 — ADR-033에서 study 항을 뺀 이유 2가지를 해소:**
① 남들은 복사본으로 공부하니 원본엔 점수가 안 쌓임 → **원본 귀속** ② 학습은 무한 반복 → **상한**.

**결정:**
- **귀속**: 학습 덱의 `originalDeckId`(없으면 자신)에 점수. 복사 시점에 최상위 원본으로 **평탄화**해 체인을 안 탐 (Codex 검산 ①). 기존 데이터는 V13에서 깊이 5까지 수습
- **단위**: 답변 수가 아니라 **(사용자, 원본 덱, 날짜) 1회** = "누적 학습자-일수". 의미가 "실제로 쓰이는 덱" 신호라 가중치 1로 낮게
- **보증**: `deck_study_days` **DB unique** + `INSERT IGNORE` 영향 행 수로 신규 판단. Redis SET은 장애·재시작·TTL로 기억이 사라져 fail-open 원칙상 보증자가 될 수 없음 (통과 질문: "최종 검증은 DB, Redis 죽으면 DB 방식" — 자기 말로 통과)
- **자기 학습 제외**: 학습자 == 귀속 덱 주인이면 0점. 자기 복사 제외(ADR-031)와 같은 기준 — 상한 없는 자기 행동은 점수에서 뺀다
- **잠금 순서**: 대상 덱 `SELECT … FOR UPDATE`(X) → 출석부 INSERT → `study_count` UPDATE. INSERT 먼저면 FK S → X 승급 = ADR-031 데드락 재현 (Codex 검산 ②). `join fetch` 없이 잠궈 users 행은 안 잠금
- **Redis 순서**: 리스너의 REQUIRES_NEW 트랜잭션 **커밋 후** ZSET +1 (`onStudied` → afterCommit). 롤백 시 Redis만 +1 남는 일 없음 (Codex 검산 ③)

**대안:**
- 답변마다 +1 — 하루 1000번 답 = 1000점, 조작 통로
- 세션 단위 1회 — 세션을 안 쓰는 Leitner 복습 경로(ReviewService)가 빠짐. 날짜 단위가 모든 모드를 덮음
- Redis SET dedupe — 빠르고 TTL 공짜지만 최종 보증 불가 (위)
- 유니크 사용자 수(distinct) — 매 조회 집계 비용, 카운터 컬럼이 정렬·재구축에 단순

**트레이드오프 / 알려진 한계:**
- 가중치가 JPQL 문자열과 자바 상수 **두 곳**에 존재 — JPQL은 상수를 못 읽음. 양쪽에 ★ 주석으로 교차 참조, 드리프트 시 DB 정렬과 Redis 순서가 어긋남
- 출석부 행이 날짜마다 쌓임(사용자×덱×일) — 현 규모 무해, 보관 기간 정책은 Phase 7 운영 항목
- PRIVATE 원본도 카운트는 쌓임(랭킹 노출은 PUBLIC만) — 나중에 공개 전환 시 이력이 살아있는 게 자연스럽다고 판단

**Codex 검산 수리 (같은 날):**
- Redis 훅(`onLiked/onUnliked/onCopied/onStudied`)이 visibility를 안 보고 `ZINCRBY` → **없는 멤버를 만들어** UNLISTED 좋아요·비공개 원본 study 점수가 순위표에 섞임 (노출은 DB 필터가 막지만 사본 오염). 훅이 `Deck`을 받아 **PUBLIC일 때만** 증분. DB 카운터는 visibility 무관하게 사실로 쌓임
- 공개 전환 `onBecamePublic`이 like·copy만 계산 → study 항 누락(재구축까지 최대 1시간 순위 오류). `score(Deck)` **자바 쪽 단일 지점**으로 통합(재구축·전환 공용). 두 버그 모두 "랭킹 off 테스트는 DB만 본다"는 검증 공백 — `DeckStudyRankingRedisTest`(Redis on) 3건으로 박제

**부수 발견 — 잠복 데드락 수리 (StatsService.recordStudy):**
6명 동시 학습 테스트가 **기존 출석부 코드**의 InnoDB 갭 락 데드락(SQL 1213)을 꺼냄. "0행 매치 UPDATE로 오늘 줄 탐색 → 없으면 INSERT" 2단계에서, 같은 순간 첫 학습인 사용자들이 같은 인덱스 갭에 갭 락을 쥔 채 INSERT를 서로 기다림. 수리 = 탐색 제거, **항상 upsert 한 방**(`ON DUPLICATE KEY UPDATE`). 교훈: **"0행을 매치하는 UPDATE도 잠금을 남긴다"** — 동시성 테스트 없이는 못 보는 종류.

---

## ADR-039: 비동기 정책 — "복구 가능한 것만 @Async" (혼합안)

**상태:** 채택 (2026-08-22)
**범위:** `AsyncConfig`(@EnableAsync, `eventExecutor` 2/4/100 + CallerRunsPolicy + AsyncUncaughtExceptionHandler), `TodaySummaryCacheListener`에 `@Async`, `DeckStudyRankingListener`는 동기 유지

**결정 기준 (통과 질문 — 사용자 자기 말):** *"사라졌을 때 복구 장치가 있나"*. 캐시 삭제는 유실돼도 TTL 5분이 만료시키고 다음 조회가 DB로 재생성 → 비동기 OK. 출석부 INSERT·study_count는 원본 기록 — 유실되면 복구할 근거가 없다 (DB unique는 "두 번 쓰지 마"를 보장하지 "반드시 한 번 써라"는 보장 안 함) → 동기로 확정.

```
학습 커밋
├─ 출석부 INSERT + study_count + ZSET +1 : 동기 (REQUIRES_NEW, 요청 스레드)
└─ 요약 캐시 삭제                          : @Async (유실 시 TTL이 복구)
```

**대안:**
- (A) 전부 동기 — 유실 0, 단순. 리스너가 무거워지면(배지·외부 호출) 응답 지연. 지금 리스너는 ms 단위라 실익은 작지만 정책 경계를 먼저 긋는 것이 목적
- (B) 전부 @Async best-effort — 커밋 직후 프로세스 종료·큐 포화 시 **원본 기록 증발**. "인기 통계는 드문 누락 허용"이라 명시할 수도 있으나 study_count는 DB 정렬의 근거라 거부
- (C) Outbox — 이벤트를 같은 트랜잭션으로 테이블에 적고 워커가 처리. 유실 0 + 비동기. 테이블·워커·재시도·멱등 = 작은 시스템. **"반드시 한 번"이 필요한 기능(포인트·결제류)이 생기면 그때** — 경로만 기록

**안전장치 (테스트 박제 `AsyncConfigTest`):**
- 큐 포화 → `CallerRunsPolicy`: 버리지 않고 호출 스레드가 직접 실행 (동기로 후퇴, 유실 0)
- void 비동기 메서드의 예외는 기본적으로 아무 데도 안 나타남 → `AsyncUncaughtExceptionHandler`가 ERROR 로그 (메서드명 포함)
- 정상 종료 시 큐 잔여 작업 완료 대기 10초 (`waitForTasksToCompleteOnShutdown`) — kill -9엔 무력, 그래서 복구 가능한 작업만

**트레이드오프 / 알려진 한계:**
- 테스트가 '곧' 일어나는 삭제를 폴링(최대 3초)으로 기다림 — 비동기의 비용이 테스트 코드에 나타남
- 워커가 죽은 채 큐만 쌓이는 상황(풀 고갈)은 caller-runs로 응답 지연으로 드러남 — 모니터링(Phase 7)에서 executor 지표 노출 후보
- 재처리(재시도) 정책: 캐시 삭제는 재시도 불필요(TTL이 대체), Redis 점수는 재구축이 대체 — 따라서 **재처리 없음이 정책** (이 ADR이 그 문서)

---

## ADR-040: 삭제 정책 = CASCADE + 안정화 묶음 (Codex 전수 감사)

**상태:** 채택 (2026-08-23)
**범위:** V15(학습 이력 FK 12개 CASCADE), `Deck @DynamicUpdate`, 가져오기 검증, 전역 400/409, Gradle↔React 연결, bat 실패 중단, 프론트 경계 5건

**배경 — 전수 감사의 결론:** "기능끼리 만나는 경계"에서 나온 버그들. 새 기능 전에 1~2세션 안정화.

**결정 1 — 삭제 정책: 학습 이력은 카드·덱과 생사를 같이한다 (DB CASCADE)**
- 전엔 12개 FK 전부 RESTRICT → 한 번이라도 학습한 카드/덱 삭제가 500
- 대안: (a) soft delete — 이력 보존되지만 모든 조회에 `deleted_at` 필터, 현 규모엔 과함 (b) 이력 있으면 삭제 거부(409) — 단어장 앱에서 "못 지움"은 사용자 적대적 (c) **CASCADE** — Quizlet 방식, deck_likes/deck_study_days(V12·V13)와 같은 판단의 확장
- 한계: 진행 중 세션의 카드를 지우면 그 세션 문제 수가 줄어듦 (허용)

**결정 2 — 덱 메타 저장은 바뀐 컬럼만 (`@DynamicUpdate`)**
- 제목 수정이 like/copy/study_count를 "읽었을 때 값"으로 같이 UPDATE → 그 사이 원자적 +1이 증발. 테스트: 수정 트랜잭션 안에서 REQUIRES_NEW로 +1 → 커밋 후 1 유지

**결정 3 — 가져오기 3구멍**: split limit 제거(4칸은 실패), DB 길이(255/200) 초과는 미리보기 실패 줄, 프론트는 미리보기 **스냅샷**을 등록 (입력이 바뀌면 등록 버튼 잠김)

**결정 4 — 전역 응답 계약**: `MethodArgumentTypeMismatch` → 400 (`?page=abc`), `DataIntegrityViolation` → 409 (첫 복습 동시 2건 등 unique 레이스의 패배 쪽). 500은 "서버 잘못"에만

**결정 5 — 빌드 경계**: `processResources → frontendBuild(npm run build)` 연결, 입력/출력 선언으로 up-to-date. bat은 빌드 실패 시 옛 jar 실행 금지(`errorlevel`)

**프론트 경계**: 퀴즈·타이핑 진행바 = 답한 수 기준 / 자동 넘김 타이머는 문제 바뀌면 취소 / 탐색 옛 응답 무시(effect alive 플래그) / 로그인 fetch 실패 메시지

**감사에서 남긴 것 (백로그)**: 비밀번호 변경·탈퇴 후 access token 최대 1시간 유효(ADR 수용 트레이드오프 — 공개 전 정책 명시), Redis ZSET 동점 순서·깨진 id fail-open, 구형 플래시카드 세션 중복 제출, 구형 덱 통계가 신형 퀴즈 미집계, 운영 DB URL useSSL, README 갱신, 프론트 자동 테스트 부재

---

## ADR-041: Phase 7 배포지 — Oracle Cloud Always Free 우선, Lightsail 예비

**상태:** 채택 (2026-08-25)
**범위:** Phase 7 전체의 기반 결정

**결정:** **Oracle Cloud Always Free (Ampere A1 Flex, Seoul)** 를 1순위로. 인스턴스 확보 실패·유휴 회수가 반복되면 **AWS Lightsail 2GB(월 ~$12)** 로 이전.

> **정정 (2026-08-26, 가입 실측):** 신규 무료 가입의 홈 리전 목록에 **한국 리전이 아예 미제공** ("korea" 검색 = No options — Oracle이 수요 폭주 리전을 무료 가입에서 숨김). 홈 리전은 가입 후 변경 불가 + "리전 바꾸려 계정 재생성 금지" 약관 명시 → **Japan East (Tokyo)로 확정**. 한국↔도쿄 ~30ms라 시연 체감 동일, 사용자의 일본 취업 계획과도 부합. Chuncheon도 동일하게 미제공이었음.

**팩트 (2026-08-25 공식 문서 실측 — 초기엔 4 OCPU/24GB로 잘못 알았던 것을 정정):**
- A1 Flex Always Free = 월 1,500 OCPU시간 + 9,000 GB시간 = **상시 2 OCPU / 12GB RAM**
- 유휴 회수: 7일간 CPU p95·네트워크·메모리 **전부** 20% 미만일 때만 — 매일 실사용이라 해당 없음
- 리스크: 무료 인스턴스 자리 확보가 리전 사정에 따라 며칠 걸릴 수 있음

**대안:** AWS EC2/Lightsail(월 1~2만 원 — 사용자의 명시적 비용 부담 + NewsPick에서 이미 경험) /
국내 무료(네이버 Micro 1GB·Cloudtype 1GB 매일 중지 — DB 포함 상시 서비스 불가)

**부수 이득:** 2/12면 VocaMaster(앱+MySQL+Redis) 외에 NewsPick 재배포까지 한 서버(nginx 리버스 프록시) 가능.
ARM(arm64) 도커 빌드 경험이 이력서 거리.

**진행 순서 (클라우드는 마지막 — 로컬에서 검증 가능한 것부터):**
① GitHub Actions CI(test+build) → ② 공개 전 보안 게이트(prod 프로필 강제·Swagger 차단·시크릿 환경변수·의존성 업데이트)
→ ③ 멀티스테이지 Dockerfile → ④ app+MySQL+Redis Compose+healthcheck (로컬 검증) → ⑤ Oracle 인스턴스 확보·배포
→ ⑥ DB 볼륨·백업 → ⑦ nginx·HTTPS → ⑧ k6 측정(Redis 전후 p50/p95/p99) → ⑨ 구형 Mustache 제거는 배포 성공 후

---

## ADR-042: Phase 7 ② 공개 배포 전 보안 게이트

**상태:** 채택 (2026-08-25)
**범위:** prod 프로필 잠금 + 의존성 현행화 (Phase 7 진행 순서의 ②)

**컨텍스트:** 지금까지는 localhost 전용이라 "편한 기본값"(Swagger 전체 공개, dev 시크릿, 2년 전 Boot)이 문제없었다. 공개 IP에 올리는 순간 이 기본값들이 전부 공격 표면이 된다.

**결정 4종:**

**1. Swagger는 운영에서 완전 비공개** — `application-prod.yml`에서 springdoc 자체를 비활성 (엔드포인트 미등록 → 404)
- 대안: (a) 인증 뒤로 숨김 — 명세는 여전히 서버에 존재, 인증 우회 버그 하나면 노출 ❌ (b) SecurityConfig에서 경로만 차단 — springdoc 빈은 살아서 메모리·부팅 낭비, 설정 2곳 관리 ❌ (c) **기능 자체를 끔** — 존재하지 않는 건 뚫릴 수 없다 ✅
- dev는 그대로 열려 있음 (프로필 분리의 존재 이유)

**2. `ProdSafetyGuard` — 잘못 설정되면 뜨지 말 것 (fail-fast)**
- 환경변수 '누락'은 스프링이 막아줌(`${JWT_SECRET}` 미해석 → 부팅 실패). 하지만 '**레포에 공개된 dev/test 시크릿을 그대로 넣는 사고**'는 아무도 안 막음 — git 히스토리에 올라간 시크릿은 이미 탄(burned) 것
- prod 프로필에서 탄 시크릿·32바이트 미만이면 명확한 한국어 메시지와 함께 부팅 거부
- 대안: 경고 로그만 ❌ — 아무도 안 읽는 경고는 없는 것. 반쯤 안전한 서비스가 뜨는 것보다 안 뜨는 게 낫다

**3. 배포 리허설을 CI에 상주 (`ProdProfileTest`)** — `@ActiveProfiles("prod")` + Testcontainers MySQL + 가짜 환경변수 4종으로 **prod 컨텍스트를 매 CI마다 실제로 부팅**
- 검증: 환경변수 자리 오타·누락 / Swagger 닫힘(404) / 안전핀 통과 / Redis 없이도 부팅(fail-open 리허설)
- "prod 설정은 배포 날 처음 실행된다"는 고전적 함정 제거 — 서버에서 겪을 부팅 실패를 CI로 앞당김

**4. 의존성 현행화** — Boot 3.3.0(2024-05, **OSS 지원 종료 = 보안 패치 중단 라인**) → 3.5.16(패치 계속 나오는 3.x 최신). springdoc 2.8.17, jjwt 0.12.7
- 대안: (a) 3.3.x 최신 패치 — 여전히 죽은 라인 ❌ (b) 4.x — Framework 7 대격변, 배포 직전에 뛸 계단 아님 ❌ (c) **3.5.16** ✅
- 부수: Hibernate 6.6·Flyway 11·Connector/J 9.7·Security 6.5로 동반 상향, Testcontainers 수동 핀 제거(BOM 1.21.4가 추월)
- **사상자 전수: 1건** — Hibernate 6.6이 '삭제된 엔티티를 참조하는 관리 엔티티'를 flush에서 단속. 운영은 요청별 영속성 컨텍스트라 무관, 같은 컨텍스트에 이어 붙인 테스트만 해당 → 경계(`em.clear`) 재현으로 수리. 두 계단 점프에 1건 = 표준 API만 쓴 덕

**부수 정리:** OSIV off(커넥션을 뷰 렌더까지 안 붙듦 — 서비스가 트랜잭션 안에서 다 조회하는 설계라 무영향, main·test yml 짝맞춤), JWT 전용이라 스프링 자동 생성 기본 계정 자동구성 제외(generated password 로그 소음 제거)

**남긴 것:** 운영 DB URL의 useSSL 판단은 Compose(④)에서 — 같은 호스트 도커 네트워크면 off 허용, 외부 관리형 DB면 required

---

## ADR-043: Phase 7 ③ 멀티스테이지 Dockerfile — node → JDK → JRE 3단

**상태:** 채택 (2026-08-25)
**범위:** 배포 산출물 포장 (Compose·배포는 ④·⑤에서)

**컨텍스트:** "어느 컴퓨터에서든 명령 한 줄"이 되려면 Node·JDK·Gradle이 전부 이미지 안에서 해결돼야 한다. 단, 그 도구들이 **최종 이미지에 남으면 안 된다** — 용량과 공격 표면.

**결정: 3단 멀티스테이지**
1. `node:20-alpine` — React 번들만 만들고 버려짐 (CI와 같은 Node 20)
2. `eclipse-temurin:17-jdk` — jar만 만들고 버려짐. 1단 산출을 받아 `-PskipFrontend`로 Gradle의 npm 단계 생략 (기존 스위치 재사용 — Docker 때문에 빌드 스크립트를 안 고침)
3. `eclipse-temurin:17-jre` — 실행기 + jar뿐인 최종 이미지

**대안:** (a) 로컬/CI에서 jar 만들고 COPY만 — 이미지가 만든 사람 환경에 의존, "레포만 있으면 빌드"가 깨짐 ❌ (b) 단일 스테이지에 node+JDK 설치 — 빌드 도구·소스가 최종 이미지에 통째로 실림 ❌ (c) **3단 분리** ✅

**보안 이어달리기 (ADR-042의 연장):**
- `USER appuser` 비root — 컨테이너가 뚫려도 root가 아니게
- `ENV SPRING_PROFILES_ACTIVE=prod` — **배포 산출물의 기본은 운영 모드**. "서버에서 깜빡하고 dev로 뜨는" 사고를 이미지 차원에서 봉쇄, 시크릿 검증은 ProdSafetyGuard가 이어받음

**기타:** 베이스 3종 모두 멀티아치(amd64/arm64 — Oracle A1 대응), `--mount=type=cache`로 재빌드 시 Gradle 재다운로드 방지, `.dockerignore`로 컨텍스트 다이어트(.git·node_modules·로컬 프론트 산출물 제외 — 이미지 안 산출만 사용해 재현성 확보), 힙 `MaxRAMPercentage=75`(컨테이너 limit 기준), 로그 시각 KST

**트레이드오프:** 이미지 빌드에서 테스트 제외 — 테스트는 CI(169개) 담당, 이미지 빌드는 포장만. 헬스체크는 Compose(④)에서

---

## ADR-044: Phase 7 ④ docker-compose.prod.yml — 배포 스택은 별도 파일·별도 프로젝트

**상태:** 채택 (2026-08-25)
**범위:** app + MySQL + Redis 3컨테이너 정의 (nginx는 ⑦에서 합류)

**컨텍스트:** 기존 `docker-compose.yml`(redis 전용)은 bat의 일상 개발 흐름이 매일 쓴다. 배포 스택을 어디에 정의할지가 문제.

**결정: `docker-compose.prod.yml` 신규 + `name: vocamaster-prod` 프로젝트 분리**
- 대안: (a) 기존 compose 확장 — bat이 `docker compose up`을 치는 순간 앱·MySQL까지 떠서 8080 충돌 + 로컬 MySQL(3306)과 혼선 ❌ (b) compose profiles — 한 파일로 되지만 새 개념 추가, 프로필 깜빡하면 (a)와 같은 사고 ❌ (c) **파일·프로젝트 분리** — 두 스택이 서로 존재 자체를 모름 ✅

**보안 (ADR-042의 연장):**
- MySQL·Redis는 **호스트 포트 미개방** — 도커 내부 네트워크 전용, 서버에서 3306/6379가 인터넷에 안 열림
- 앱의 DB 계정은 root가 아닌 전용 계정(`vocamaster`) — MySQL 컨테이너가 첫 기동 때 생성
- 시크릿은 전부 `.env`(.gitignore) — 커밋되는 파일에는 자리표시만, `.env.example`이 작성 안내 (dev 시크릿을 넣으면 ProdSafetyGuard가 거부하는 것까지 한 세트)

**기동 순서:** `depends_on` + condition — MySQL은 `service_healthy`(준비 완료까지 앱 대기), Redis는 `service_started`(fail-open 설계라 떠 있기만 하면 충분). 앱 healthcheck는 bash `/dev/tcp` TCP 연결(JRE 이미지에 curl 없음) — actuator 도입 시 승격.

**리허설 실측 (2026-08-25, 로컬):** 처녀 볼륨에 Flyway **15판 전부 성공** → 3컨테이너 (healthy) → 가입(200)→로그인(JWT 발급)→인증 API(200) 왕복. `down -v` 후 dev 환경 무영향 확인. 앱 응답까지 30초.

**기타:** app `mem_limit 2g`(이미지의 MaxRAMPercentage=75와 한 쌍 — 12GB 서버에서 NewsPick 동거 대비), 운영 Redis는 AOF 영속화 켬(재시작 시 랭킹 워밍 생략), MySQL·Redis 버전은 테스트 컨테이너와 동일 라인(운영-테스트 동등), `APP_PORT` 환경변수로 로컬 리허설(8083)과 서버(8080) 공용.

---

## ADR-045: Phase 7 ⑤ 배포 토폴로지 — A1 품귀 실측 후 x86 Micro 1GB×2 분산

**상태:** 채택·배포 완료 (2026-08-26)
**범위:** 실서버 구성 (도쿄 리전)

**컨텍스트:** 목표였던 A1 Flex(2 OCPU/12GB)는 **15시간 210회 자동 시도 전부 "Out of capacity"** (1/6 축소분도 0회 성공 — 도쿄 무료 ARM은 사실상 매물 0). 대안 검토: (a) PAYG 전환 — 업그레이드 시 **~$100 카드 승인**이 표준 절차인데 사용자 체크카드 잔고 사정상 기각 (b) 유료 VPS — 사용자 비용 기준(월 2~3천 원) 초과 (c) **Always Free의 남은 카드: x86 `VM.Standard.E2.1.Micro`(1 OCPU/1GB) 2대** — A1과 별도 무료 한도, 인기가 없어 재고 흔함 → **첫 발에 2대 확보**.

**결정: 앱/DB 서버 분리 (1GB 한 대엔 전체 스택이 안 들어감)**
- `vocamaster-app`(10.0.0.79): Spring 앱 단독 — `deploy/docker-compose.app.yml`, mem_limit 700m(힙 ~520m)
- `vocamaster-db`(10.0.0.131): MySQL 8 + Redis — `deploy/docker-compose.db.yml`, performance_schema OFF(~150MB 절약)
- 같은 서브넷 사설 IP로 통신. 두 박스 모두 swap 2G (1GB RAM 필수 보강)

**보안 계층 (ADR-042·044 연장):** Security List = 인터넷에서 **22만** 허용, 3306/6379는 `10.0.0.0/24` 내부망 소스만, 앱 8080은 `127.0.0.1` 바인딩 — **HTTPS(⑦) 전까지 인터넷 완전 비공개**, 검증은 서버 내부 curl·SSH 터널만. 시크릿은 서버에서 `openssl rand`로 생성해 각 서버 `.env`에만 존재 (레포·채팅 무기록).

**이미지 전달:** 레지스트리 없이 `docker save | gzip | ssh docker load` (565MB) — 서버에서 빌드하면 1GB라 OOM. 로컬(x86)과 서버 아키텍처가 일치해 가능했던 방식 (A1이었다면 buildx 필요했음).

**배포 실증 (2026-08-26):** 처녀 DB Flyway 15판 자동 적용 → SPA 200 · 공개 API 200 · 미인증 401 · Swagger 404 · **가입→학습 2건→summary 200(total 2/accuracy 50)→stats 200** (P0-1 수리의 실서버 검증) · Redis 랭킹 경로 200. 앱 박스 메모리: swap 64Mi만 사용.

**남긴 것:** A1 사냥꾼은 계속 가동 — 잡히면 단일 박스로 이사(무료 한도 별도라 공존 가능). k6(⑧)는 "1GB 무료 2대"의 실측으로 기록. VCN 잔해 10개 청소는 ⑥에서.

---

## ADR-046: Phase 7 ⑦ 공개 — DuckDNS 무료 도메인 + nginx + Let's Encrypt

**상태:** 채택·공개 완료 (2026-08-26)
**결정:** `https://vocamaster-app.duckdns.org` — 무료 서브도메인(DuckDNS) + nginx 리버스 프록시 + Let's Encrypt 인증서(자동 갱신 타이머).

**대안:** (a) 유료 도메인(연 1.5~2만 원) — 비용 원칙(월 0원) 위배, 나중에 nginx `server_name` 갈아타기로 언제든 승격 가능 ❌지금은 (b) IP 직접 공개 — HTTPS 불가(평문 로그인 금지 정책 위반) ❌ (c) **DuckDNS** — DNS는 전화번호부일 뿐, 빌린 서브도메인이어도 인증서·자물쇠는 100% 진짜 ✅

**구성:** 사용자가 DuckDNS에 도메인+서버 IP 등록(vocamaster는 선점돼 -app) → Security List 80/443 개방 → **OS iptables도 개방**(OCI 우분투 이미지는 INPUT에서 22 외 REJECT — nginx는 호스트 프로세스라 Docker와 달리 이 벽에 걸림, 유명한 함정) → nginx가 443 종단·127.0.0.1:8080으로 프록시(앱은 계속 인터넷 비노출) → certbot `--redirect --hsts`.

**보안 헤더:** HSTS(1년)·nosniff·X-Frame·Referrer-Policy. 공개 후 외부 경로 실측: HTTPS 200 / HTTP→301 / Swagger 404 / **HTTPS 경유 JWT 로그인 왕복 200** (Secure 쿠키도 이제 유효).

**기록:** 8/25 계정 생성 → 8/26 자정 A1 사냥 개시 → 개강 전날 밤 공개까지 만 24시간.

---

## ADR-047: 구글 로그인 — 세션형이 아닌 JWT 다리 방식

**상태:** 채택 (2026-08-26 밤)
**컨텍스트:** NewsPick의 구글 로그인은 `oauth2Login` 기본형(세션) — 키만 넣으면 끝이었다. VocaMaster는 STATELESS JWT(리프레시 회전·재사용 탐지)라 구글 인증이 성공해도 우리 토큰이 없으면 전 API가 401. **그 간극을 잇는 다리가 이번 작업의 전부.**

**구성:**
- `OAuth2SuccessHandler`: 구글 성공 → email_verified 확인 → `loginWithGoogle`(기존 issueTokens 재사용) → refresh 쿠키(AuthController와 동일 속성) 심고 `/app/login?oauth=success`로 → SPA가 그 쿠키로 `/auth/refresh` 호출해 로그인 완성 (기존 401→refresh 인프라 재활용)
- 켜고 끄기: `google.client-id` 비면 oauth2Login 자체가 안 붙음 — 테스트·로컬 dev는 기존과 완전 동일 (조건부 등록)
- V16: password NULL 허용 + provider 컬럼. 구글 계정에 비번 로그인 시도 → NPE가 아니라 안내 400
- **같은 이메일 자동 연결**: 기존 이메일 가입자가 구글로 오면 같은 계정 (구글의 이메일 검증 신뢰 — 로컬·실서버 모두 실측 확인)
- 함정 2개 격파: ① AuthService→SecurityConfig(PasswordEncoder)→핸들러→AuthService **순환 참조** → 핸들러를 생성자 대신 @Bean 메서드 파라미터로 ② nginx 뒤 redirect_uri가 http로 생성 → `forward-headers-strategy: framework`
- 부속: 새 구글 콘솔은 게시에 홈페이지·개인정보처리방침 URL 필수 → `/privacy.html` 신설(permitAll), 프로필 팝오버(아바타 클릭 → /users/me, provider 뱃지)

**테스트:** 175개 (신규 3: 신규 가입 / 자동 연결 / 구글 계정 비번 로그인 400) + 로컬 브라우저 실왕복.

---

# 운영 규칙 — 앞으로 새 결정마다

1. **결정 *전*에** 이 파일에 ADR 추가 (또는 `docs/decisions/ADR-NNN-제목.md`로 분리)
2. 양식:
   - 상태 / 범위
   - 컨텍스트 (*왜* 결정이 필요했나)
   - 고려한 대안 3개 이상 (각각 ❌ 이유)
   - 결정 + 근거
   - 트레이드오프 / 알려진 한계
3. *대안이 1개*만 떠오르면 *아직 충분히 고민 X*. 더 찾기.
4. *대안 없는 결정*은 결정이 아니라 *자동 선택*. ADR 안 적어도 됨.
5. 5~10개 누적되면 `docs/decisions/` 디렉토리로 분리 (방향 B로 전환).
