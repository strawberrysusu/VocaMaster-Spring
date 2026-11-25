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

**정정 (2026-11-09):** 위 헤더를 nginx와 앱(Spring Security)이 둘 다 붙여 응답마다 두 벌씩 나가고 있었다(X-Frame-Options는 `DENY`·`SAMEORIGIN` 두 값). 브라우저가 실제로 쓰던 값이 앱 쪽이므로 nosniff·X-Frame-Options(DENY)·HSTS(1년, includeSubDomains)는 앱 한 곳으로 두고, 앱이 안 붙이는 Referrer-Policy만 nginx에 남겼다. 같은 날 nginx에서 HTTP/2를 켜고, 클라이언트가 보낸 `Forwarded`·`X-Forwarded-Host/Port/Prefix/Ssl`을 지우고, `X-Forwarded-For`는 실제 접속 주소 하나로 덮어쓰게 했다. Spring `ForwardedHeaderFilter`가 이 헤더들을 믿기 때문에, 그대로 두면 요청자가 리다이렉트 주소를 바꾸고 앱의 HSTS를 끌 수 있었다(운영 실측). 적용 후 실측: 보안 헤더 각 1줄, ALPN `h2`, 위조 헤더를 보내도 기준과 같은 Location·HSTS, 구글 `redirect_uri`도 https 그대로.

**기록:** 8/25 계정 생성 → 8/26 자정 A1 사냥 개시 → 개강 전날 밤 공개까지 만 24시간.

---

## ADR-047: 구글 로그인 — 세션형이 아닌 JWT 다리 방식

**상태:** 채택 (2026-08-26 밤)
**컨텍스트:** NewsPick의 구글 로그인은 `oauth2Login` 기본형(세션) — 키만 넣으면 끝이었다. VocaMaster는 STATELESS JWT(리프레시 회전·재사용 탐지)라 구글 인증이 성공해도 우리 토큰이 없으면 전 API가 401. **그 간극을 잇는 다리가 이번 작업의 전부.**

**구성:**
- `OAuth2SuccessHandler`: 구글 성공 → email_verified 확인 → `loginWithGoogle`(기존 issueTokens 재사용) → refresh 쿠키(AuthController와 동일 속성) 심고 `/app/login?oauth=success`로 → SPA가 그 쿠키로 `/auth/refresh` 호출해 로그인 완성 (기존 401→refresh 인프라 재활용)
- 켜고 끄기: `google.client-id` 비면 oauth2Login 자체가 안 붙음 — 테스트·로컬 dev는 기존과 완전 동일 (조건부 등록)
- V16: password NULL 허용 + provider 컬럼. 구글 계정에 비번 로그인 시도 → NPE가 아니라 안내 400
- ~~**같은 이메일 자동 연결**: 기존 이메일 가입자가 구글로 오면 같은 계정~~ → **철회 (2026-08-28, ADR-048)**: 이메일 가입엔 소유 검증이 없어 선가입 공격자의 계정에 진짜 주인이 합류하는 pre-hijacking 경로였음 (Codex 검산). 이제 local 계정이 있으면 구글 로그인을 거부하고 이메일/비밀번호 로그인으로 안내. email_verified는 "구글 사용자가 이메일 주인"만 증명할 뿐, 기존 계정의 주인은 증명하지 못한다.
- 함정 2개 격파: ① AuthService→SecurityConfig(PasswordEncoder)→핸들러→AuthService **순환 참조** → 핸들러를 생성자 대신 @Bean 메서드 파라미터로 ② nginx 뒤 redirect_uri가 http로 생성 → `forward-headers-strategy: framework`
- 부속: 새 구글 콘솔은 게시에 홈페이지·개인정보처리방침 URL 필수 → `/privacy.html` 신설(permitAll), 프로필 팝오버(아바타 클릭 → /users/me, provider 뱃지)

**테스트:** 175개 (신규 3: 신규 가입 / 자동 연결 / 구글 계정 비번 로그인 400) + 로컬 브라우저 실왕복.

---

## ADR-049: V19 수리 건수 정오표 — 진단 쿼리의 콜레이션 비대칭 (V21 불요 판정)

**상태:** 확정 (2026-08-30, 백업 복원 실측)
**컨텍스트:** V19 주석은 오염을 "A형 3,369 / B형 4,755"로 기록했는데, 8/29 백업 검증에서 B형이 4,690으로 집계 — 65건 차이의 원인 설명이 필요했다 (Codex 최종 검산 지적). 적용된 마이그레이션은 수정 금지이므로 이 ADR이 정오표 역할을 한다.

**실측 (V19 직전 백업 `20260828-1930`을 임시 스키마에 복원, 총 9,627장 — 진단 시점과 동일 = 시점차 0):**

| | 진단 카운트 (8/28) | V19 실제 수리 | 차이 = 반각 주석 오산입 |
|---|---:|---:|---:|
| A형 (front `単語（よみ）`) | 3,369 | **2,951** | 418 |
| B형 (back `（よみ）뜻`) | 4,755 | **4,690** | 65 |
| 합계 | 8,124 | **7,641** | 483 |

**원인:** 진단 쿼리(`front LIKE '%（%）%'` / `back LIKE '（%'`)는 **COLLATE 명시가 없어 기본 utf8mb4_0900_ai_ci가 전각（）과 반각()을 동일시** — `(機械を)稼働`, `(목)소리` 같은 정당한 반각 주석 483건이 오염으로 오산입됐다. 수리(V19)는 `COLLATE utf8mb4_bin`으로 전각만 정밀 타격했으므로 이 483건을 **옳게 제외**했다. 즉 틀린 것은 V19가 아니라 주석에 받아적은 진단 수치.

**추가 검증 (전부 0 확인):** 기존 reading 보유 행 0 / B형 가드(`）` 뒤 뜻 없음)로 제외된 전각 행 0 / A형 전각 중 정확형 아닌 행 0 / **현재 prod 잔존: V19 정확형 0 + 느슨형(`front LIKE '%（%）%' AND reading 빈`) 0**.

**결정:** V19 무결 — 보정 마이그레이션(V21) 불요. 데이터 변경 없이 문서 기록만.

**교훈:** 전각/반각이 섞이는 데이터에서는 **진단 쿼리도 수리 쿼리와 같은 COLLATE로** 작성한다. 수리만 bin으로 쓰고 진단을 ai_ci로 둔 비대칭이 "수리 건수가 안 맞는" 유령을 만들었다. (실은 8/28 진단 결과의 `A_zenkaku=A_hankaku`, `B_zen=B_han` 동일 카운트가 이미 경고 신호였다.)

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

---

## ADR-050: 학습 세션 일괄 제출 — 답변 즉시 저장에서 '제출 전 답안 수정'으로

**상태:** 채택 (2026-09-01) · **범위:** 플래시카드 복습(`/study`) 저장 계약

### 컨텍스트

사용자 지적: *"카드 학습하는데 이미 넘어갔으면 Quizlet 마냥 다시 돌아와서 안다/모른다 넘기거나"*.

문제의 뿌리는 UI가 아니라 저장 시점이었다. `ReviewService.recordAnswer`는 답변 즉시 Leitner 박스를 움직이고, **오답은 `boxLevel`을 1로 풀 리셋**하면서 `correctStreak`까지 0으로 날린다. 이전 값은 어디에도 남지 않는다. 즉 "되돌리기"를 하려면 사라진 값을 복원해야 한다.

Quizlet의 되돌리기가 쉬운 이유는 그 화면의 안다/모른다가 **세션 안에서만 사는 값**이기 때문이다. 우리는 그 버튼이 곧바로 영구 상태를 바꾼다.

### 고려한 대안

1. **직전 1장 되돌리기 API** — `CardProgress`에 `prev_box_level`·`prev_correct_streak` 컬럼을 추가하고 undo 엔드포인트 신설.
   ❌ 1단계만 되돌아간다. 사용자가 원한 건 "3장 전으로 가서 고치기"에 가깝다. 되돌리기 이력을 더 쌓으면 표가 커진다.
2. **읽기 전용 뒤로가기** — 이전 카드를 다시 볼 수만 있고 판정은 수정 불가.
   ❌ 가장 싸지만 "잘못 눌렀는데 못 고침"이 그대로 남는다. 핵심 불편을 해결하지 못한다.
3. **죽어 있던 `StudySession`/`StudyRecord` 부활 + `deck_id` nullable** — 배치를 세션 엔티티로 표현.
   ❌ 한때 검토했으나 철회. 프론트가 이미 안 쓰는 레거시를 되살리는 비용이 본 기능보다 컸다. `study_sessions.deck_id NOT NULL` 완화가 `getSessionSummary`의 `deckTitle`, `StudyRecordRepository`의 `r.session.deck.id`, `getDeckStats` 집계까지 **다섯 지점**으로 번진다. YAGNI.
4. **일괄 제출 (채택)** — 세션 중 답은 프론트의 임시 상태, '완료' 한 번으로 전체 확정.

### 결정

`POST /reviews/answers/batch` 하나가 세션 전체 답안을 **한 트랜잭션**에 반영한다.

- 사용자가 원한 것은 사실 "되돌리기"가 아니라 **제출 전 답안 수정**이었다. 임시 답안으로 두면 앞뒤 이동·수정이 그냥 된다 — 되돌릴 것 자체가 없다.
- 세션 엔티티를 만들지 않았다. 카드마다 `loadOwnedCard`가 소유권을 다시 보므로 **여러 덱이 섞여도 그냥 된다** — `/study` 전체 복습의 다중 덱 문제가 통째로 증발한다.
- 멱등은 `review_submissions` (V21) 의 `(user_id, submission_id)` unique가 최종 보증. 프론트의 더블클릭 방어는 보증자가 못 된다.
- 중복 판정은 **`INSERT IGNORE`의 반환값 0**으로 안다. 같은 트랜잭션에서 unique 위반을 예외로 catch하면 rollback-only에 걸려 뒤이은 조회가 못 나간다 (`deck_study_days` V13과 같은 관용구).
- 처리권을 뺏긴 쪽의 조회는 **잠금 읽기**(`PESSIMISTIC_READ`)여야 한다. REPEATABLE READ의 일반 SELECT는 자기 스냅숏을 봐서 이긴 쪽의 커밋을 놓친다. 못 읽으면 409.
- 통계는 `study_count += answerCount`를 **한 번**, `StudyRecordedEvent`는 **distinct 덱마다 한 번**. 단건 경로를 200번 부르면 UPDATE 200회 + 이벤트 200발이 나가고, 랭킹 리스너가 덱 행에 X 잠금을 200번 잡았다 놓는다.

### 트레이드오프 / 알려진 한계

- **잃은 것:** 답변 직후의 "직전 카드 → 박스 N" 표시. 박스는 완료 시점에만 움직이므로 세션 중엔 보여줄 값이 없다. 완료 화면 합계로 대체.
- **중간 이탈 시 미반영.** `sessionStorage` 초안(서버 성공 응답 후에만 삭제)으로 새로고침은 복구되지만, 브라우저를 아예 닫으면 그 세션은 반영되지 않는다. 대신 '그만하기'에서 확인 창을 띄운다.
- **재전송 응답에 `results`(카드별 박스)를 담지 못한다.** 그때의 박스 값을 저장하지 않기 때문. 지금 `CardProgress`를 읽으면 그 사이 다른 학습으로 바뀐 값일 수 있어 `null`로 비운다. 필요해지면 `boxLevelAfter` 컬럼을 검토.
- 기존 `POST /reviews/cards/{id}/answer`(단건)는 **그대로 남긴다.** Leitner 규칙은 `applyAnswer` 한 곳만 쓰므로 두 경로가 갈라지지 않는다.
- **번호 주의:** ADR-049가 말한 "V21 불요"는 V19 데이터 보정용 V21을 뜻한다. 실제 `V21__add_review_submissions.sql`은 이 ADR의 것으로, 서로 무관하다.

### 검증

- 신규 테스트 6개 (`ReviewBatchAnswerTest`): 카드마다 1회 반영 / 재전송 시 박스 불변 / 중복 `cardId` 400 / 남의 카드 섞이면 403 + **앞 카드까지 전체 롤백** / 다중 덱 정상 / `study_count`가 답변 수만큼 1회 증가
- 전체 스위트 **186개 통과, 실패 0**
- 자동 롤백(`@Transactional`)을 끈 이유: 멱등은 앞선 제출이 *커밋된 뒤* `INSERT IGNORE`가 0을 반환해야 성립하고, 전체 롤백은 진짜 트랜잭션 경계가 있어야 검증된다.

### 보강 (2026-09-01, Codex 검산 반영)

첫 구현 뒤 검산에서 정적 렌더·백엔드 테스트가 못 잡는 구멍 6개가 나와 닫았다.

| # | 구멍 | 조치 |
|---|---|---|
| 1 | `알아요` 더블클릭·Enter 연타 | `advancing` ref로 전환 잠금. **타이머가 아니라 `idx` 변경 effect에서 해제**한다 — 타이머로 풀면 느린 기기에서 여전히 두 칸 넘어간다. 겸해서 포커스를 카드로 옮겨 연타가 방금 누른 버튼을 다시 때리지 않게 함 |
| 2 | 커밋 성공 + 응답 유실 후 재전송 불가 | payload를 `queue`가 아니라 **저장된 `answers` 스냅샷**에서 만든다. 갱신된 카드는 다음 복습일로 밀려 `/reviews/due`에서 사라지므로 queue 기준이면 영영 못 보낸다. 큐가 비어도 초안이 남아 있으면 **'제출 재시도' 복구 화면**을 띄운다 |
| 3 | 같은 ID + 다른 답안이 조용히 무시됨 | `review_submissions.payload_hash` (cardId 정렬 후 SHA-256). 같은 해시면 멱등 응답, **다른 해시면 409**. 순서만 다른 재전송은 정렬 덕에 같은 해시 |
| 4 | 동시 재전송 미검증 | 스레드 2개가 같은 `submissionId`를 동시에 쏘는 통합 테스트 추가. 진행도·`study_count` 각 1회만 반영 확인 |
| 5 | 자정 경계에 시각이 갈림 | `recordAnswers` 진입에서 KST `now`를 **한 번만** 뽑아 모든 `CardProgress` 시각과 통계 날짜(`now.toLocalDate()`)에 흘려보냄. `StatsService.recordStudy`에 날짜를 받는 오버로드 추가 |
| 6 | 손상된 초안 | `JSON.parse` 성공만 보지 않고 `submissionId` 문자열 길이와 `answers`의 키(양의 정수)·값(boolean)까지 검증. 하나라도 어긋나면 통째로 버림 |

**되돌린 회귀 하나 —** 재작성 과정에서 판정 버튼이 뒷면 확인 전에도 눌리게 바뀌어 있었다. 원래 정책(`revealed`일 때만 노출)이 맞으므로 **비활성으로 복원**했다. 숨기지 않고 비활성만 하는 이유: 되돌아온 카드에서 자기가 뭘 골랐는지는 보여야 한다.

**초안의 수명 (정정):** `sessionStorage`는 **탭이 닫히면 함께 사라진다.** 새로고침은 살아남지만 탭 종료는 아니다 — 처음 주석에 "닫아도 다음 진입에서 복구된다"고 잘못 적었다. 실제 방어선은 두 겹이다: '그만하기'의 확인 창, 그리고 탭 종료·이탈을 붙잡는 `beforeunload` 경고. 그래도 강제 종료되면 그 세션은 반영되지 않는다.

**복구 판정은 '큐가 비었는가'가 아니다 (2차 검산):** 처음엔 `total === 0 && 초안 있음`으로 잡았는데, 10장 중 3장만 답하고 응답이 유실되면 미응답 7장이 due에 남아 큐가 0이 아니다. 그러면 복구 화면이 안 뜨는 데서 끝나지 않고, 남은 7장을 마저 답해 제출하는 순간 payload가 달라져 **payload_hash 검사에 409로 걸리고 초안을 버릴 UI도 없어 막다른 골목**이 된다. 복구(2)와 해시(3)를 각각 옳게 만들고 둘의 상호작용을 못 본 것. → 판정 기준을 **"초안의 cardId 중 지금 큐에 없는 것이 하나라도 있는가"**로 바꿨다. 그 카드는 이미 반영돼 다음 복습일로 밀려났다는 뜻이므로.

**동시성 테스트의 거짓 초록:** 첫 판은 `catch (RuntimeException)`으로 아무 예외나 결과값으로 받아 NPE·예상 밖 500도 통과할 수 있었다. 허용 결말을 `BatchAnswerResponse` 또는 `ConflictException` 둘로 못 박았다.

**범위 밖 백로그:** 카드 조회 IN 배치 최적화, 오래된 `review_submissions` 정리, 자동 읽기·음성 속도 설정.

### 3차 보강 (2026-09-01, 브라우저 스모크 + Codex 2차 검산)

**복구 판정을 큐 크기로 하면 안 되는 이유가 due 모드만이 아니었다.** 고아 감지(`초안의 cardId 중 큐에 없는 것`)는 due 모드에서만 통한다 — 덱 학습(`/study?deckId=N`)은 큐가 항상 그 덱의 전 카드라 고아가 생기지 않는다. 그래서 덱 모드에서는 응답 유실 후 남은 카드를 마저 답해 제출하면 payload가 달라져 `SUBMISSION_MISMATCH` 409에 막히고, 초안을 버릴 UI도 안 보여 **여전히 막다른 골목**이었다.

→ 서버가 이미 409로 알려주고 있으므로 그것을 신호로 쓴다. 두 겹이 됐다:

| 감지 | 언제 | 어디서 통하나 |
|---|---|---|
| 고아 카드 | 새로고침 직후, 답한 카드가 due에서 사라졌을 때 | due 모드 (선제적) |
| `SUBMISSION_MISMATCH` 409 | 제출을 시도했을 때 | **모든 모드** (안전망) |

**메시지 문자열 파싱을 막기 위해 code를 실었다.** 일괄 제출의 409는 두 종류이고 대응이 정반대다 — 일시 경합은 *같은* ID로 재시도, 답안 불일치는 *새* ID로 제출. `ConflictException`에 `code`를 두고 `ErrorResponse`로 내보내며, 프론트는 `ApiError`(status·code를 나르는 `Error` 서브클래스)로 받는다. 기존 `(e as Error).message` 호출부는 그대로 동작한다.

**제출 payload를 동결한다 (3차 검산에서 뒤집은 결정).** 처음엔 "새 제출로 전부 다시 보내고, 이미 반영된 카드가 한 번 더 세어지는 건 감수한다"로 갔다. 근거는 *"클라이언트는 앞선 제출에 어떤 카드가 들어 있었는지 알 방법이 없다"* — **이 전제가 틀렸다.** payload를 만든 게 클라이언트다. 보내기 직전에 그것을 저장하면 된다.

```
answers    = 제출 전 자유롭게 고치는 답안
attempted  = POST를 시도한 순간 동결된 답안  ← 있으면 편집 금지, 재시도는 이것만
```

- 제출 버튼 → **네트워크보다 먼저** 현재 payload를 `attempted`로 `sessionStorage`에 쓴다. 순서가 뒤바뀌면 응답 유실 시 '무엇을 보냈는지'를 잃는다
- `attempted`가 있는 동안 답 수정·새 답변을 막는다. 동결본과 달라지면 재시도가 '다른 답안'이 되어 409를 맞는다
- 실패·유실·새로고침 뒤에는 **같은 submissionId + 같은 내용**으로만 재시도한다. 이미 처리됐으면 멱등 응답, 아직이면 이번에 처음 반영
- 성공 후 초안을 지우고, 남은 카드는 **새 세션 ID**로 따로 학습한다
- 그래서 `SUBMISSION_MISMATCH`는 정상 UX 분기가 아니라 **초안 손상·프론트 버그를 잡는 최후 방어선**이 됐다. 나오면 재시도를 막고 '버리기'만 남긴다

이 결정으로 unique 제약과 payload hash가 비로소 제 역할을 한다 — 그전에는 클라이언트가 스스로 다른 payload를 만들어 보내서 두 장치를 무력화하고 있었다.

**함정 하나 (실측):** 성공 직후 `clearDraft()`만 하면 자동저장 이펙트가 `attempted` 변화에 반응해 방금 지운 초안을 되살린다. `answers`까지 비우고 `result`가 있으면 쓰지 않도록 막아야 한다.

**스모크 실측 (localhost dev, MySQL·Redis 실제 기동, Flyway v16→v21):**
- 9/9 통과 — 토글 / 뒷면 전 비활성 / 이전-답변경 / **알아요 5연타에도 한 칸만 이동** / 새로고침 복구 / 부분 제출 / 재제출 멱등(같은 답 200·순서만 다름 200·답 변경 409) / 다중 덱 + 별표 초기 상태 / 별표 500 시 UI 원복
- 단일 `now` 실증: 한 배치의 세 카드 `last_reviewed_at`이 `13:51:45.065887`로 마이크로초까지 동일
- 막다른 골목 두 시나리오 재현 후 탈출 확인 — due 모드, 덱 모드

**4차 검산 — payload 동결 실측 (덱·due 두 모드):** 4장 중 3장 답 → 서버 커밋 후 응답만 유실(fetch를 가로채 서버에는 보내고 응답만 버림) → 새로고침 시 학습 화면이 감춰지고 재시도만 노출 → 재시도가 **정확히 같은 submissionId + 같은 3장**을 전송 → `alreadySubmitted` → 초안 삭제 → 남은 1장은 새 세션 ID로 별도 제출.
결과: `alpha/bravo/charlie` **box 2 · streak 1**(동결 전 방식이면 box 3 · streak 2), `delta` box 1, `study_count` 4 = 3+1, 영수증 2행. **중복 승급 0.**

### 후속 (2026-09-01, 배포 후 사용자 결정)

**뜻을 보지 않고도 채점할 수 있게 했다.** 원래 판정 버튼은 `revealed`일 때만 눌렸다 — ADR-050 재작성 과정에서 실수로 이 제약이 빠졌다가, "기존 정책"이라는 이유로 되돌렸다. 그때 검산자가 *"이게 의도인지 확인하라"*는 단서를 달았는데 사용자에게 묻지 않고 복원한 것이 잘못이었다.

배포 후 사용자가 직접 써보고 지적: **아는 단어마다 '뜻 확인' 탭이 하나씩 더 붙는다.** 복습 덱은 아는 카드가 대부분이라 그 한 탭이 매 카드에 쌓인다. 자기평가 학습에서 '떠올렸으면 바로 채점'이 자연스럽고, Quizlet도 앞면에서 바로 눌린다. → `disabled={!revealed}` 제거, 안내 문구도 "떠올렸으면 바로 답해도 되고, 카드를 눌러 뜻을 확인해도 돼요"로 변경.

**교훈:** '기존 동작'은 그 자체로 근거가 아니다. 제품 결정은 사용자 몫이고, 검산자가 "의도인지 확인하라"고 하면 확인해야 한다.

**백로그로 남긴 것 (배포 직후 손대지 않음):**
- (11/14 주: 아래는 Oracle 시절 실측 — 9/30 AWS 이전 뒤 운영 MySQL은 `@@system_time_zone`=KST라 지금은 해당 없음) `review_submissions.created_at`이 `NOW(6)`(컨테이너 MySQL = UTC)라 KST 통일 원칙에서 벗어난다. 운영 실측으로 확인: 같은 사건이 영수증 `06:06:09`(UTC) / 진행도 `15:06:09`(KST). 읽는 코드가 없어 지금 깨지는 건 없다
- 백업 스크립트가 비밀번호를 명령줄 인자로 넘긴다(`ps`에 노출). `--defaults-extra-file`로 전환
- 카드 조회 IN 배치 최적화, 오래된 영수증 정리

---

## ADR-051: 타이핑·듣기 채점 — 괄호 설명·품사 표시를 걷어내고 비교 (ADR-026 결정 B 확장)

**상태:** 채택 (2026-11-14)
**범위:** 새 `common/AnswerMatcher.java`, `TypingService.isAnswerMatch`, 새 `frontend/src/lib/answerMatch.ts`, `Listening.tsx`, 공유 사례표 `src/test/resources/answer-match-cases.json`, CI `Frontend unit test` 단계, `docs/typing-policy.md`

### 컨텍스트
11/13~14 운영 단어장 19,156장을 전수 검토해 827장을 고쳤다(뜻 보충 624장은 `"멸사봉공 (사사로운 이익을 …)"`처럼 뜻 뒤에 괄호 풀이). 반영 직후 확인해 보니 타이핑(단어→뜻)과 듣기 모드의 뜻 채점이 **쉼표 조각과 통째로 같아야 정답**이라, 괄호가 붙은 조각은 핵심 뜻을 쳐도 오답이 됐다.

운영 카드에 지금 규칙과 새 규칙을 돌려 본 실측 (뜻에서 첫 번째 핵심 뜻을 쳤을 때):
- 지금 규칙으로 정답이 되는 카드 **12,105장 / 19,156장**
- 새 규칙으로 **19,156장 전부**. 늘어난 7,051장의 대부분은 보충 때문이 아니라 원래 있던 문제였다 — 어셔 영어 단어장 뜻이 `"n. 수학"`, `"a. 사소한, 시시한"`처럼 품사 표시로 시작해서, 영어 타이핑은 사실상 `"n. 수학"`을 통째로 쳐야만 정답이었다.

### 결정
정답 칸에서 뽑는 **후보를 넓힌다** (입력 쪽은 그대로).
1. 예전 쉼표 조각은 그대로 후보로 둔다 → 전에 정답이던 입력은 계속 정답 (뒤로 호환)
2. 거기에 더해, 괄호 설명(겹괄호·전각 포함)을 지우고, 영어 품사 표시 `n. v. a. ad. …`와 `①②`를 구분자로 바꾼 뒤 `, ， ; ； / ／ ・`로 나눈 조각
3. 비교 정규화: 물결표 `~ ～ 〜` 삭제, 연속 공백 하나로, 앞뒤 공백 제거, 대소문자 무시. **단어 중간 띄어쓰기는 그대로** (ADR-026의 "app le ≠ apple" 유지)

### 고려한 대안
- **A. 데이터에서 설명을 빼서 따로 저장 (메모 칸)** — 근본적으로 맞는 방향이고 화면에 "설명 줄"로 보이는 기능과 함께 후속 단계로 진행한다. 하지만 원래 괄호가 있던 카드(약 1,750장)와 품사 표시 문제는 이것만으로 안 풀린다.
- **B. ✅ 채점 규칙 확장** — 원인이 데이터 모양이 아니라 "뜻 칸에는 설명이 섞여 있다"는 현실이라, 채점이 그 현실을 받아들이는 쪽이 맞다.
- C. 오타 허용(Levenshtein) — 여전히 STRETCH (ADR-026과 같은 이유)

### 두 구현을 한 표로 묶기
타이핑은 서버(Java), 듣기는 프론트(TS)가 채점한다. 규칙이 갈라지면 같은 답이 모드마다 다르게 채점된다. 그래서 사례표 하나(`answer-match-cases.json`, 47건)를 두 쪽 테스트가 같이 읽는다 — 서버 `AnswerMatcherTest`(DynamicTest), 프론트 `npm test`(`scripts/test-answer-match.mjs`: 테스트 러너를 새로 들이지 않고 이미 있는 `typescript`로 `.ts`를 변환해 불러온다, CI Node 20에서도 동작). CI에 프론트 단계를 추가했다. 정규식은 프론트에서 뒤보기(lookbehind)를 쓰지 않는다 — 오래된 iOS Safari는 파일 전체를 못 읽는다.

### 트레이드오프 / 한계
- 괄호 속에 쉼표가 있으면 예전 쉼표 조각(괄호 속 문장 조각)도 여전히 후보다 — 뒤로 호환을 지키는 대가
- 영어 품사 목록(`n v a ad adv adj prep conj pron int phr pl`) 밖의 약어는 구분자로 보지 않는다
- 과거 attempt는 재채점하지 않는다 (ADR-026과 같음)

### 보완 (11/14 검토 워크플로 → PR #29) — 공백 규칙을 두 구현이 글자 하나까지 같게
- 문제: 두 구현 모두 마지막에 언어 기본 `trim()`을 썼는데, **Java `trim()`은 U+0020 이하만, JS `trim()`은 유니코드 공백까지** 지운다. 정답 끝에 얇은 공백(U+2009)·좁은 NBSP·BOM 같은 게 붙으면 타이핑(서버)만 오답이었다. 반대로 U+0001 같은 제어 문자는 서버 `trim()`만 지웠다. (운영 카드 24,845장에는 이런 글자가 0장 — 붙여넣기로 들어올 때를 막는 것)
- 결정: 공백 목록 `SPACE`를 서버·프론트에 **한 곳씩 똑같이 적고**, `trim()`·`\s`는 안 쓴다. 순서 = ① 폭 없는 글자(U+200B·200C·200D·2060·FEFF) 삭제 → ② 물결표 삭제 → ③ 공백(어떤 종류든) 여러 개 → 보통 공백 하나 → ④ 앞뒤 공백 하나 제거 → ⑤ 소문자 → ⑥ 끝 시그마 ς를 σ로(대문자 Σ의 소문자를 Java·JS가 다르게 골라서). 품사 표시 뒤 공백 판단도 같은 `SPACE`. 두 파일의 `SPACE` 문자열은 글자 그대로 같다(VT도 둘 다 `\u000B` — Java 정규식의 `\v`는 세로 공백 묶음 전체라 옮겨 적으면 함정).
- Java의 끝 기호는 `$` 대신 `\z` — Java `$`는 끝에 줄바꿈 글자가 있으면 그 앞에서도 맞아 JS와 달랐다.
- 타이핑 제출은 손대지 않은 입력으로 채점한다(`TypingService`의 Java `trim()`은 저장·표시용으로만).
- 사례표 47 → 59건(얇은 공백·좁은 NBSP·BOM·제로폭 공백·넓은 공백·NEL·제어 문자·끝 시그마). E2E: 정답 끝에 얇은 공백이 붙은 카드를 타이핑으로 맞히기.
- 검증(별도 에이전트): 모든 코드 포인트(U+0000~U+10FFFF)를 22개 틀에 넣어 대조 + 무작위 57만 쌍 → 이 수정 뒤 정규식 단계의 차이 0 (같은 도구로 고치기 전 코드는 2만 쌍 중 3,239건 차이). 남은 차이는 아래 한계 하나.
- **한계**: 대소문자 무시(⑤)는 실행 환경의 유니코드 버전을 따른다 — 서버 JRE 17 = Unicode 13, 화면 = 브라우저 ICU. Unicode 14 이후 새 글자(예: U+A7C0 Ꟁ, 비트쿠치 문자) 95자는 서버·화면이 다르게 채점할 수 있다. 단어장 글자(한·일·영)와는 무관해 코드로 맞추지 않는다(A-Z만 접으면 Ä·Ａ 같은 글자의 대소문자 무시를 잃는다).

---

## ADR-052: 종이 시험지 — 브라우저에서 Word(.docx)를 직접 만든다 (라이브러리 없음)

**상태:** 채택 (2026-11-14)
**범위:** 새 `frontend/src/lib/testSheet.ts`(문항), `frontend/src/lib/docx.ts`(WordprocessingML + 무압축 zip), `components/TestSheetDialog.tsx`, `DeckDetail.tsx` "시험지" 버튼, 프론트 테스트 `scripts/test-test-sheet.mjs`

### 컨텍스트
사용자 학습법은 "모르는 단어 ⭐ → 다시 보기". 학원에서 하던 쓰기 시험(한자 → 읽기·뜻)을 앱 덱으로 하고 싶다 — 사용자가 보낸 Word 양식(문서1.docx)에 단어가 들어간 채로 받아서 인쇄하고 손으로 쓴다. 채점·별표는 종이를 본 사람이 직접(온라인 시험지의 자동 ⭐는 다음 단계).

### 결정
- **브라우저에서 .docx를 만든다.** 덱 화면이 이미 카드를 다 불러와 있으니 서버 API·DB 변경이 없다.
- **라이브러리 없이** WordprocessingML 5개 파일(`[Content_Types].xml`, `_rels/.rels`, `word/document.xml`, `word/styles.xml`, `word/_rels/document.xml.rels`)을 **무압축(store) zip**으로 묶는다 — CRC32·로컬 헤더·중앙 디렉터리 약 80줄.
- 양식은 사용자 문서를 실측해 맞춤: A4 11906×16838 twip, 여백 위 1701·나머지 1440, 칸 높이 1020 twip(1.8cm), 같은 너비 칸. 더한 것: 좁은 번호 칸, 쪽마다 반복되는 머리줄(`tblHeader`), 행이 쪽 사이에서 안 쪼개짐(`cantSplit`), 제목·날짜·점수 줄, 맨 뒤 정답지(촘촘한 행).
- 일본어 덱 = 번호·단어·읽기·뜻, **한자 없는 단어는 읽기 칸 회색**. 영어 덱(일본어 0) = 번호·단어·뜻.

### 고려한 대안
- **서버에서 Apache POI** — jar에 수 MB 의존성 + 2GB 서버 메모리에서 문서 생성. 데이터가 이미 브라우저에 있는데 왕복할 이유가 없다.
- **`docx` npm 라이브러리** — 편하지만 번들이 커지고(내부에 zip 라이브러리 포함), 필요한 건 표 하나라 직접 쓰는 XML이 짧다.
- **브라우저 인쇄(HTML)만** — 사용자가 원한 건 "Word 양식 그대로 받아서 인쇄". 칸 높이·쪽 나눔을 Word 쪽이 더 정확히 지킨다.

### 검증
- `npm test`(CI): CRC32 표준 값(`The quick brown fox …` = 0x414FA339), zip을 Word와 같은 순서(EOCD → 중앙 디렉터리 → 로컬 헤더)로 다시 읽어 항목·CRC 대조, 행 수·회색 칸 수·이스케이프·여백 등 40건
- 로컬 1회: 실제 덱(N1 Day09 30문항, 어셔 토플 day02 20문항)으로 만든 파일을 Python `zipfile.testzip()` 통과, **Word(COM)로 열어 PDF로 내보내기** — 표 2개·행 31/21, 5쪽·3쪽, 화면 확인. 쪽수 어림값은 이 실측으로 맞춤(문제지 첫 쪽 11행·이후 12행, 정답지 26행)

### 한계
- 압축하지 않아 파일이 크다(30문항 약 96KB) — 인쇄용이라 문제없음
- 쪽수는 어림값(뜻이 길면 정답지 행이 두세 줄로 늘어난다)

### 보완 (11/14 검토 워크플로 → PR #28)
- 읽기 칸은 **괄호 밖 낱말(시험 보는 말)에 한자가 있을 때만**. `(話が)こじれる`·`めきめき(上達する)`처럼 문맥에만 한자가 있으면 가나 낱말로 보고 회색 칸(온라인 시험은 '—'). `危ぶまれる(開催が)`는 그대로 읽기 칸.

---

## ADR-053: 온라인 시험지 — 틀린 단어 자동 ⭐ + 별표 일괄 설정 API(멱등)

**상태:** 채택 (2026-11-14)
**범위:** 새 `PATCH /decks/{deckId}/cards/star` (`SetStarredRequest`/`SetStarredResponse`, `CardService.setStarred`), 새 `pages/OnlineTest.tsx`(`/test/:deckId`), `TestSheetDialog` "온라인으로 풀기", `answerMatch.matchesReading`·`foldKana`

### 컨텍스트
종이 시험지(ADR-052)와 같은 문항을 화면에서 풀고 싶다. 사용자 결정: **채점하면 틀리거나 비워 둔 단어는 자동으로 ⭐** (버튼이 아니라 자동). 복습 일정(라이트너)은 건드리지 않는다.

### 결정
- 채점은 프론트에서 (듣기 모드와 같음) — 뜻은 ADR-051 규칙, 읽기는 같은 규칙에 **가타카나→히라가나 접기**(`カンシャ` = `かんしゃ`). 한자가 없는 단어·읽기 데이터가 없는 단어는 읽기를 묻지 않는다.
- 비슷하게 쓴 뜻은 기계가 틀렸다고 하니 행마다 **'맞음으로'**. 누르면 **이 시험이 새로 켠 ⭐만** 끈다(시험 전부터 ⭐였던 카드는 그대로). '다시 오답으로'면 다시 켠다.
- 별표는 새 **일괄 설정 API**로: 값 지정(멱등) + 한 번에 여러 장 + 응답에 `changedIds`(실제로 값이 바뀐 카드).
- 답안은 채점 전까지 `sessionStorage`에 (순서·답) — 새로고침해도 남는다. Enter = 다음 칸, 단 일본어 입력기 변환 중(`isComposing`)의 Enter는 건드리지 않는다.

### 고려한 대안
- **기존 토글 API(`PATCH /cards/{id}/star`)를 틀린 카드마다 호출** — 화면이 들고 있는 별표 상태가 낡았으면(다른 기기에서 바꿈) 켜려던 별표를 **꺼 버린다**. 재시도·두 번 클릭에도 결과가 뒤집힌다. 장수만큼 요청도 나간다.
- **타이핑 모드처럼 서버 세션으로 채점·저장** — 남길 기록이 없다(부수 효과는 별표뿐). 채점 규칙은 이미 프론트·서버가 같은 사례표로 묶여 있다(ADR-051).

### API 계약
- 덱 주인만(403), 빈 목록·`starred` 없음 400, 한 번에 500장까지
- ~~덱에 없는 카드·없는 카드가 하나라도 섞이면 400 + 아무것도 안 바꿈~~ → **11/14 보완 3**: 이 덱의 카드만 바꾸고, 나머지 id는 건너뛰어 `skippedIds`로 알려 준다 (아래)
- 벌크 UPDATE가 아니라 엔티티로 바꾼다 — 벌크는 `@UpdateTimestamp`를 건너뛰어 `updated_at`이 안 바뀌고, 값이 실제로 바뀐 카드만 UPDATE 하기도 어렵다 (11/14 정정: 처음엔 'DB `NOW()`가 UTC'라고 적었으나 운영 MySQL은 `@@system_time_zone`=KST로 실측됨)
- 테스트: `CardServiceTest` 4건(바뀐 것만·멱등 / 해제 / ~~원자적 거절~~ → 지워진·다른 덱 카드 건너뜀(보완 3) / 403), `CardStarBulkHttpTest` 3건(200 JSON + skippedIds / 400 검증 / 로그인 필요)

### 보완 (11/14 Codex 검산 3건 → PR #27, 화면 회귀 테스트로 재현 후 수리)
- **500장 상한**: '전체' 시험은 문항 수 제한이 없어 오답이 500개를 넘을 수 있었다 → 한 번에 다 보내면 400. 화면(`api/cards.ts setStarred`)이 **500장씩 나눠 차례로** 보낸다. 서버 상한은 그대로(요청 크기 보호). 중간에 실패하면 앞 묶음은 반영된 채 오류 — 값 지정이라 처음부터 다시 보내도 같다.
- **'이 시험이 켠 ⭐' 판정**: `changedIds`만 믿으면, 서버엔 저장됐는데 응답이 끊긴 경우 다시 시도의 `changedIds`가 비어 '맞음으로'가 그 ⭐를 못 껐다. → **시험을 열 때 이미 ⭐였던 카드(starredAtLoad)**를 기억하고, 오답 중 그 밖의 카드는 **요청을 보내기 전에** '이 시험이 켠 ⭐'로 넣는다. 실제로 안 켜졌다면 끄기 요청은 아무것도 안 바꾼다(멱등). `changedIds`는 계속 합친다(시험 중 다른 곳에서 꺼진 ⭐를 다시 켠 경우).
- **늦은 응답**: '맞음으로' 저장 중에 '다시 풀기'를 누를 수 있어, 늦게 온 응답이 새 문항의 같은 번호(다른 단어)를 정답으로 바꿨다. → 별표 저장 중(채점 직후 일괄·맞음으로·다시 오답으로)에는 '다시 풀기'를 막고, 풀이 차수(attempt)가 바뀐 뒤 도착한 응답은 결과 표시를 건드리지 않는다.
- 남는 한계: 시험 도중 **다른 탭에서** 같은 카드에 ⭐를 켜고 그 카드를 틀린 뒤 '맞음으로'를 누르면, 그 ⭐도 이 시험이 켠 것으로 보고 끈다(서버가 누가 켰는지 기록하지 않음). 드문 경우라 받아들인다.
- 테스트: `e2e/tests/online-test.spec.ts` 회귀 3건 — PR #26에서 `test.fail`로 고치기 전 실패를 확인, 이 PR에서 표시를 떼고 통과.

### 보완 2 (11/14 검토 워크플로 → PR #28)
- 답안 임시 저장(sessionStorage)에 **이 시험이 켠 ⭐(`added`)**도 넣는다. '다시 풀기' 중에 새로고침하면 그 기록이 사라져, 복원된 시험에서 '맞음으로'가 앞 풀이가 켠 ⭐를 못 껐다. 복원할 때 그 카드는 starredAtLoad에서 뺀다. 첫 풀이의 임시 저장엔 빈 배열(채점하면 임시 저장은 지운다).

### 보완 3 (11/14 검토 워크플로 → PR #29) — 지워진 카드는 건너뛴다
- 문제: 시험지를 연 채로 다른 탭·기기에서 그 덱의 카드를 하나 지우면, 채점 때 보내는 별표 요청이 '없는 카드가 섞임'으로 **통째 400** — 나머지 틀린 단어도 ⭐가 안 켜지고 '별표 다시 시도'도 영원히 실패했다.
- 결정: 서버가 **이 덱의 카드만** 바꾼다(`CardRepository.findByDeckIdAndIdIn`). 보낸 id 중 이 덱에서 못 찾은 것(지워진 카드, 다른 덱 카드)은 바꾸지 않고 `skippedIds`(오름차순)로 알려 준다. 화면은 "지워진 카드 N개는 건너뜀"을 붙이고 그 id를 '이 시험이 켠 ⭐'에서 뺀다.
- 지워진 카드와 다른 덱 카드를 **똑같이** 건너뛰므로, 응답만 보고 남의 카드 id가 있는지 알 수 없다(예전 400도 둘을 구별하지 않았다). 덱 주인 확인(403)은 그대로.
- 테스트: `CardServiceTest` "지워진 카드·다른 덱 카드는 건너뛰고 이 덱 카드는 바꾼다"(예전 '원자적 400' 테스트를 대체), `CardStarBulkHttpTest` 응답에 `skippedIds`, E2E "시험 도중 카드가 지워져도 나머지는 ⭐".

### 보완 4 (11/14 최종 검토 → PR #31)
- 자동 ⭐가 실패한 채로(`별표 다시 시도`가 뜬 상태) **'다시 풀기'를 막는다** — 실패한 ⭐를 다시 시도하지 않고 다음 풀이로 넘어가면 그 단어들은 끝내 ⭐가 안 켜졌다. 버튼 설명: "별표를 먼저 다시 시도해 주세요".
- **'새로 켠 별표 N개'는 서버가 켰다고 확인한 카드만** 센다(`confirmedOn`). 요청 전에 넣는 '이 시험이 켠 ⭐'(`addedStars`)는 '맞음으로'가 끌 카드를 정하는 데만 쓴다 — 실패한 요청의 카드까지 세서 수가 부풀던 것.
- '맞음으로'·'다시 오답으로' 요청 중에는 **'별표 다시 시도'도 막는다** — 그 단어가 아직 오답으로 보여서 방금 맞음으로 한 단어에 ⭐가 다시 켜졌다.
- E2E: "자동 ⭐가 실패한 채로는 다시 풀기가 막히고, 다시 시도가 성공하면 풀 수 있다", "맞음으로 저장 중에는 별표 다시 시도를 누를 수 없다" — 고치기 전 jar에서 먼저 실패 확인.

### 보완 5 (11/15 → PR #40) — 퀴즈·타이핑·듣기의 오답도 자동 ⭐
- 사용자 요청('온라인으로 풀 때 틀린 건 자동 별표'). 범위는 사용자 결정: **퀴즈·타이핑·듣기의 오답.** 낱말카드 복습의 '몰라요'는 켜지 않는다 — 새 덱은 거의 다 '몰라요'라 별표가 전부 켜지고, '별표만 복습'이 '전체 복습'과 같아진다.
- **퀴즈·타이핑(서버가 채점)**: 답 제출 트랜잭션 안에서 `Card.star()` — 이미 ⭐이면 쓰지 않고, `@DynamicUpdate`라 `starred` 칸만 UPDATE 된다. 채점과 같은 트랜잭션이라 오답이 저장되면 별표도 반드시 켜진다. 세션은 덱 주인만 시작할 수 있어(`verifyOwner`) 남의 카드에 별표가 켜질 일은 없다.
- **함정 (테스트가 잡음)**: 처음엔 `reviewService.recordAnswer(...)` 뒤에서 켰는데 저장되지 않았다. 그 안의 출석 upsert가 `@Modifying(flushAutomatically = true, clearAutomatically = true)`라 실행 뒤 영속성 컨텍스트를 비우고, 그 뒤에 고친 카드는 추적이 끊긴 객체였다. `recordAnswer` **앞**으로 옮겼다 — upsert 직전의 자동 flush가 별표까지 같이 써 준다. 두 서비스에 주석으로 남겼다.
- **듣기(화면이 채점)**: 채점 직후 틀리거나 비워 둔 문항(스펠링·뜻 중 하나라도)을 일괄 별표 API로 켠다 — 값 지정이라 두 번 보내도 같고, 500장씩 나눠 보낸다. 실패하면 점수 옆에 '별표 다시 시도'. 시험 도중 지워진 카드는 건너뛴다(보완 3).
- 끄는 것은 자동으로 하지 않는다 — 한 번 맞혔다고 외운 것은 아니다. 별표는 사용자가 직접 끈다.
- 화면: 시작 전 '틀린 단어는 자동으로 ⭐ 돼요', 결과에 '틀린 단어는 자동으로 ⭐ 했어요' + '⭐ 별표만 복습 하러 가기'.
- 테스트: `LearningModesProgressTest` +3(퀴즈·타이핑 오답 → ⭐, 낱말카드 '몰라요'는 그대로), E2E `auto-star.spec.ts` 3(퀴즈: 틀린 것만 ⭐·맞힌 것은 그대로 / 타이핑 / 듣기: 빈칸 포함).

---

## ADR-054: 설명 줄 — 뜻 칸의 보충 풀이를 memo 칸으로 분리해 작은 글씨로 보인다

**상태:** 채택 (2026-11-14)
**범위:** `DueCardResponse.memo`, `CardService` memo 빈 문자열 → null, 프론트 학습 카드·덱 목록·카드 추가/수정·온라인 시험 결과·Word 정답지, 운영 데이터 이동(뜻 보충 624장)

### 컨텍스트
11/14 단어장 검토에서 어려운 한자어 624장에 `"멸사봉공 (사사로운 이익을 …)"`처럼 뜻 칸 뒤에 괄호 풀이를 붙였다. 뜻 칸이 길어지자 타이핑·듣기 채점이 깨졌고(ADR-051로 수리), 4지선다 '뜻→단어' 방향에서는 풀이 속 낱말이 답을 흘렸다. `cards.memo`(V1부터 있음, 복사 시 공유 콘텐츠 — ADR-031)는 API까지 다 있는데 **화면 어디에도 안 나왔다**.

### 결정
- 풀이는 **memo**에 둔다. 뜻(back)은 다시 짧게 — 채점·퀴즈 선택지·문제는 뜻만 쓴다.
- memo가 보이는 곳: 학습 카드(뜻을 확인한 뒤, 뜻 아래 작은 글씨), 덱 단어 목록(뜻 아래), 온라인 시험 채점 결과, Word 정답지(뜻 아래 작은 회색 줄). 카드 추가·수정 칸에 "설명(선택)".
- 안 보이는 곳: 퀴즈·타이핑·듣기(답이 길어지면 안 됨), 공개 덱 화면(`PublicCardResponse`는 그대로 — 공개 API 범위를 넓히는 건 따로 결정).
- memo도 reading처럼 `""`를 보내면 삭제(null), 앞뒤 공백 제거.
- 운영 데이터: 보충 624장 중 595장은 `"뜻 (풀이)"` 꼴이라 그대로 나누고, 풀이가 뜻 중간에 낀 29장은 `"낱말: 풀이"` 꼴로 옮긴다(4장은 손으로 다듬음). 옮기기 전 값·되돌리기 SQL을 남긴다. 화면 배포 **뒤에** 옮긴다(먼저 옮기면 풀이가 잠시 안 보인다).

### 고려한 대안
- **괄호를 뜻에 그대로 두기** — 채점은 ADR-051로 풀었지만, 뜻이 길어 학습 카드·선택지가 무거워지고 답이 새는 문제는 남는다.
- **새 컬럼(`explanation`)** — memo가 이미 같은 자리(카드당 자유 텍스트, 500자, 복사 대상)라 마이그레이션만 늘어난다.

## ADR-055: 화면 회귀 테스트 — 빈 DB에 띄운 앱을 Playwright로 누르고, 실패하면 배포하지 않는다

**상태:** 채택 (2026-11-14)
**범위:** `e2e/`(Playwright 1.64.1), `.github/workflows/ci.yml`의 `e2e` 잡, `deploy`가 `e2e`를 기다림

### 컨텍스트
서버 테스트(284개, Testcontainers)와 프론트 검사(채점 규칙·시험지 105개)는 PR마다 돌지만, **화면(브라우저)은 아무것도 자동으로 보지 않았다**. 11/14 화면 버그 두 개(1024px 모드 버튼 세로 깨짐, 카드 추가 줄 179px 넘침)는 테스트가 아니라 손으로 사이트를 둘러보다 찾았다. 같은 날 Codex 검산이 찾은 온라인 시험 별표 문제 3건도 누르는 순서·늦은 응답처럼 화면 흐름에서만 드러나는 종류였다.

### 결정
- CI `build`가 만든 jar를 `e2e` 잡이 받아 **빈 MySQL 8.0.46·Redis 7.2.16(운영과 같은 판)**에 띄우고(dev 프로필 + 일회용 DB 값), Chromium으로 누른다. 계정은 실행마다 새로 가입(무작위 비밀번호), 덱은 테스트마다 새로 만든다. 운영 서버·운영 데이터는 쓰지 않는다.
- 보는 것: 로그인 화면, 덱 화면 설명 줄·카드 추가(설명 포함), 학습 카드 설명 줄, Word 시험지 받기(zip 서명·본문에 단어), 타이핑 괄호·품사 채점, 온라인 시험 자동 ⭐·맞음으로(원래 ⭐는 그대로), 폭 375·820·1024·1280에서 주요 화면 13곳 가로 넘침 0과 모드 버튼 한 줄, 폰 두 크기에서 학습 카드 한 화면. 모든 테스트에서 브라우저 오류(잡히지 않은 예외·console.error) 0.
- **알려진 버그는 `test.fail()`로 먼저 재현해 둔다**("지금은 실패해야 정상"). 고치는 PR에서 표시를 떼면 같은 테스트가 회귀 방지로 바뀐다. 처음 재현한 4건: Codex 3건(맞음으로 저장 중에 다시 풀기, 오답 501개 이상이면 별표 실패, 응답 유실 뒤 맞음으로가 ⭐를 못 끔) + 띄어쓰기 없는 긴 덱 제목의 가로 넘침(이 테스트가 처음 찾음 — 덱 목록·덱 화면·온라인 시험·홈).
- `deploy`는 `needs: [build, e2e]` — 화면 테스트가 실패하면 배포하지 않는다. 테스트 폴더는 `frontend/` 밖(`e2e/`)이라 테스트만 바꾼 커밋은 배포(재시작)를 일으키지 않는다.
- CI에서만 실패 시 1회 다시 돌린다(일시 오류 대비, 다시 해서 통과하면 보고서에 flaky로 남는다). 실패하면 스크린샷·trace·앱 로그를 아티팩트로 올린다.

### 고려한 대안
- **운영 사이트 스모크 스크립트** — 운영 데이터에 테스트 흔적이 남고, 로그인 정보를 CI에 둬야 하며, 배포 전에 막지도 못한다.
- **API 목(mock)으로 프론트만** — 빠르지만 서버 계약(한 번에 500장 상한, 별표 멱등 응답)을 못 본다. Codex 2번은 실제 서버여야 드러난다.
- **화면 캡처 비교** — 글꼴·렌더 차이로 흔들리기 쉽다. 지금 필요한 건 "가로 넘침 0·한 화면" 같은 규칙 검사다.

### 보완 (11/14 최종 검토 → PR #31)
- **배포 여부는 '지금 운영에서 도는 커밋'과 비교해 정한다.** 예전엔 직전 push(`github.event.before`)와 비교해서, 앱 커밋의 화면 테스트가 실패해 배포가 막힌 뒤 테스트만 고친 커밋이 오면 그 앱 변경이 영영 배포되지 않았다(새 커밋 기준으로는 '앱 변경 없음'). 이제 deploy 잡이 SSH로 운영 앱 컨테이너의 이미지 라벨 `org.opencontainers.image.revision`을 읽어 그 커밋..이번 커밋의 앱 경로 변경을 본다. 운영 커밋을 못 읽으면 앱 변경을 놓치지 않게 배포한다. (SSH 키 준비 단계를 변경 감지 앞으로)
- 실패 자료 올리기는 **시간 초과로 취소돼도**(`failure() || cancelled()`), Playwright `globalTimeout` 15분 — CI 잡 제한(20분) 안에서 끝나 앱 로그·trace가 남게.
- 폰 화면 온라인 시험 테스트가 **시험지 안에서 잘려 안 보이는 넘침**까지 본다(시험지의 scrollWidth). README 로컬 실행에 `npm ci --prefix frontend` 추가.

## ADR-056: 다국어(일본어·영어) — 한국어 문구를 열쇠로 쓰는 작은 t()

**상태:** 채택 (2026-11-14)
**범위:** `frontend/src/i18n/`(index.ts·ja.ts·en.ts), `components/LangSwitch.tsx`, 화면 26개 파일, `scripts/test-i18n.mjs`, `e2e/tests/i18n.spec.ts`

### 컨텍스트
사용자 요청: 한국인 전용 화면을 일본어·영어(다음에 중국어) 사용자도 고를 수 있게. 화면 문구가 26개 파일 549줄에 한국어로 박혀 있었다. 일본 취업 포트폴리오라 일본 면접관이 일본어 화면으로 직접 써 볼 수 있는 것도 이유.

### 결정
- **한국어 문구 자체가 열쇠**(gettext 방식): `t('채점하기')`. 한국어 화면은 열쇠를 그대로 보여 줘서 예전과 글자 하나 다르지 않다 — 기존 화면과 E2E 선택자가 그대로다. 코드를 읽을 때도 한국어가 그대로 보인다.
- 사전: `ja.ts`(열쇠 목록의 기준)와 `en.ts`(`Messages` 타입 = ja의 열쇠). 빠진 열쇠·남는 열쇠, `t()`에 넣은 사전에 없는 열쇠는 **tsc가 컴파일 때** 잡는다. 바뀌는 값은 `{n}` 자리로: `t('틀린 {n}개만 다시 풀기', { n })`.
- 언어 고르기: 설정 화면 맨 위와 로그인 화면 오른쪽 위(🌐 Language). 브라우저에 저장(`vm.settings.lang`), 고른 적 없으면 브라우저 언어(일본어·영어, 그 밖은 한국어). 바꾸면 새로고침 — 파일이 처음 불릴 때 정해지는 문구(메뉴 이름 등)까지 새 언어로.
- 라이브러리 없음(react-i18next 대신 60줄 남짓). 복수형 규칙이 필요 없게 문구를 짰다(영어 `Cards: {n}`, `{n} cards`).
- Node 단위 테스트가 읽는 lib(answerMatch·testSheet·docx·keys)는 i18n을 가져오지 않는다 — Word 시험지 칸 이름·파일 이름은 매개변수(기본값 한국어 = 예전과 같은 파일)로 받고, 시험지 창이 번역해서 넘긴다.
- 작업 방식: 화면 파일 26개를 7묶음으로 나눠 에이전트 7개가 문구를 `t()`로 바꾸고 번역을 돌려줬다 → 사전 하나로 합침(열쇠 504개, 묶음끼리 번역이 갈린 10개는 한쪽으로 통일).
- 검사: `test-i18n.mjs` — 두 사전 열쇠 일치, 빈 값 없음, `{n}` 자리 일치, 번역에 한글 없음, 코드의 `t('…')` 열쇠가 모두 사전에 있음. E2E `i18n.spec.ts` — 새 계정(덱·카드도 한글 없이)으로 일본어·영어 각각 화면 13곳 + 시험지 창 + 온라인 시험 채점 결과에 한글 0, 한국어로 되돌리면 예전 화면.

### 용어표 (ko → ja / en)
덱 デッキ/deck · 카드 カード/card · 단어 単語/word · 뜻 意味/meaning · 읽기 読み/reading · 설명 説明/note · 별표 スター/star · 학습 学習/study · 복습 復習/review · 퀴즈 クイズ/quiz · 타이핑 タイピング/typing · 듣기 リスニング/listening · 시험지 テスト用紙/test sheet · 온라인 시험 オンラインテスト/online test · 채점하기 採点する/Grade · 맞음으로 正解にする/Mark correct · 다시 오답으로 不正解に戻す/Mark wrong · 알아요 わかる/I know · 몰라요 わからない/I don't know · 탐색 探す/Explore · 내 덱 マイデッキ/My decks · 일괄 가져오기 一括インポート/Bulk import

### 고려한 대안
- **react-i18next** — 복수형·지연 로딩 같은 기능은 많지만 의존성과 배울 것이 늘고, 문구 500개 규모엔 과하다.
- **의미 열쇠(`deck.addCard`)** — 코드에서 한국어가 사라져 읽기·검색이 어렵고, 기존 화면·E2E를 건드릴 위험이 크다.

### 한계
- 같은 한국어가 문맥마다 다른 번역이 필요하면 열쇠를 나눠야 한다(지금은 없음).
- 서버가 보내는 오류 문구(로그인 실패 등)는 아직 한국어 — 다음 PR. 사용자 데이터(단어장 내용)는 번역하지 않는다. 중국어는 다음 단계.

### 2단계 (11/14 → PR #32): 중국어(간체) + 서버 오류 문구
- **중국어** 사전 `zh.ts`(간체, `html lang="zh-CN"` — 글꼴이 중국어 자형을 고르게). 화면 문구 505개를 에이전트 3개가 나눠 옮김. 용어: 덱 卡组 · 카드 卡片 · 뜻 释义 · 읽기 读音 · 설명 备注 · 별표 星标 · 복습 复习 · 시험지 试卷 · 채점하기 评分 · 맞음으로 改为正确 · 알아요 认识 · 몰라요 不认识 · 탐색 发现 · 듣기 听写.
- **서버 오류 문구**: 서버는 그대로 한국어로 보내고 화면이 `tServer()`로 번역한다 — 사전에 그대로 있으면 번역, 숫자가 끼는 문구는 사전의 `{n}` 틀과 모양을 맞춰 보고(`현재 1개` ↔ `현재 {n}개`), 입력 검사 오류(`필드: 문구, …`)는 조각마다(필드 이름은 그대로). 모르는 문구는 그대로 둔다(뜻이 사라지는 것보다 한국어라도 보이는 게 낫다). 서버 문구 51개(로그인 실패·비밀번호 규칙·세션·가져오기 한도 등)를 세 사전에 추가. 적용 지점은 두 곳: `api()`가 만드는 `ApiError`, 로그인 화면(직접 fetch).
- 대안: 서버가 `Accept-Language`로 번역(MessageSource) — 서버 55곳을 바꾸고 화면 사전과 이중 관리가 된다. 오류 코드만 보내고 화면이 번역 — 지금은 대부분 `BAD_REQUEST` 하나라 문구별 코드가 없어 서버 대공사.
- 테스트: 사전 검사에 중국어(열쇠 일치·자리·한글 없음), E2E 언어 3개 × 화면 13곳 + 시험지 창 + 채점 결과에 한글 0, 로그인 실패 문구가 일·영·중으로(로그인 안 한 새 브라우저).

### 3단계 (11/14 → PR #33): 다시 읽기 다듬기
- 검토 에이전트 3개(읽기만 — 일본어·중국어+영어·실행 확인, 약 90만 토큰)가 찾은 것을 고침. 번역: 일본어 48곳(링크 공유 = 限定公開, 별표 요약 문장, 통계 낱말, 줄임표 …·쌍점 통일 등), 중국어 15곳(로그인 소개 문장, 문장 사이 — 를 ，·。로), 영어 7곳(← Previous, Unlisted 등).
- **빠졌던 서버 문구 7개**: 서버 내부 오류·동시 처리 충돌 2개·경로 없음·본문(JSON) 읽기 실패·파라미터 형식·토큰 만료.
- **가져오기 '너무 김' 사유**: 서버가 실패 줄 끝에 한국어 사유를 붙여 보낸다 → `translateImportLine()`이 알려진 꼬리만 번역한다(줄 내용은 사용자 글이라 그대로). 서버 상수(255·200)나 문구가 바뀌면 단위 검사가 서버 코드와 비교해 잡는다. 대안(사유를 따로 보내는 필드)은 API 변경이라 보류.
- **영어 단수**: `englishOne()` — 사전은 복수형 하나로 쓰고, 그 자리에 들어갈 수가 1이면 틀만 단수로(`1 cards` → `1 card`). 채운 값(사용자 글)은 건드리지 않는다. 단위 검사가 영어 사전 전체를 1로 채워 복수가 남지 않는지 본다.
- **글꼴**: 일본어·중국어 화면은 Noto Sans JP·SC를 그 언어일 때만 불러와 `html:lang()`으로 앞에 둔다 — 한국어 글꼴로 그리면 한자가 한국식 자형이 되고, 간체자는 다른 글꼴로 섞인다. 한국어·영어 화면은 더 받는 것이 없다. Word 시험지도 같은 이유로 일본어 = Yu Gothic·ja-JP, 중국어 = Microsoft YaHei·zh-CN (한국어 출력은 수정 전후 해시가 같음을 확인).
- **Accept-Language**: 요청에 고른 언어를 싣는다 — `message`를 안 적은 기본 입력 검사 문구(@NotBlank·@Email 등, Hibernate Validator)가 그 언어로 온다.
- 작은 것: 덱 설명 칸 열쇠 분리(`덱 설명 (선택)` — 카드 설명 칸과 다른 낱말), 타이핑 정답 따옴표를 언어별로(「」·“”), 일본어·중국어는 문구 조각 사이를 띄우지 않는 `gap()`.
- 남긴 것: `privacy.html`(개인정보처리방침)은 한국어 그대로 — 법적 문서라 번역본을 둘지는 따로 정한다.

---

## ADR-057: Codex 확장 검토 수리 — 바뀐 컬럼만 저장·잠금 순서·입력 경계 (ADR-040 확장)

**상태:** 채택 (2026-11-14 → PR #34)
**배경:** Codex가 11/14 변경 74개 파일과 연결된 인증·저장·학습·시험 경로를 넓게 검토해 9건을 냈다(#8·#9는 PR #33에서 수리). 검증 에이전트 5개(읽기만, 약 165만 토큰)가 9건을 코드로 확인하고 같은 뿌리의 형제 문제를 더 찾았다. 항목마다 회귀 테스트를 먼저 쓰고, 고치기 전 코드에서 실패하는 것을 본 뒤 고쳤다.

**결정 1 — 바뀐 컬럼만 저장 (User·Card `@DynamicUpdate`, ADR-040 결정 2를 넓힘)**
- 전체 컬럼 UPDATE면 '읽었을 때의 값'을 같이 써서 그 사이 커밋된 변경을 되돌린다: 닉네임 저장이 비밀번호 변경·탈퇴를, 비밀번호 변경이 탈퇴를(설정 탭 두 개로 가능), 별표 저장이 고친 설명을, 설명 저장이 켜진 별표를.
- `CardService.update`·`toggleStar`는 트랜잭션이 없어서(open-in-view=false) 읽은 카드가 detached → save()의 merge가 낡은 값 전체를 덮어써 `@DynamicUpdate`만으로는 안 된다 → `@Transactional` (DeckService.update와 같은 이유). 빠져도 다른 테스트는 통과해 버려서, 붙어 있는지 직접 보는 테스트를 둔다.
- 별표 토글은 DB가 한 번에 뒤집는다(`CardRepository.toggleStarred`) — 읽고 뒤집어 쓰면 겹친 두 번(빠른 두 번 클릭·두 탭)이 한 번으로 줄었다.
- `@Version`은 쓰지 않음: 쓰는 컬럼이 서로 달라 바뀐 컬럼만 쓰면 충돌 자체가 없어진다. @Version이면 별표 클릭이 409가 되어 화면이 처리해야 하고, 컬럼 추가 마이그레이션도 든다.

**결정 2 — 잠금 순서와 잠금 실패 계약**
- 일괄 제출은 cardId 오름차순으로 반영한다 — card_progress 행 잠금을 늘 같은 순서로 잡아, 순서만 다른 동시 제출의 교착(MySQL 1213)을 없앤다. 지문(해시)은 원래도 정렬 후 계산이라 멱등 그대로. `results`도 이 순서(화면은 쓰지 않음).
- `PessimisticLockingFailureException`(교착·잠금 대기 초과) → 409 "같은 요청이 동시에 처리되었습니다. 다시 시도해 주세요". 그 트랜잭션은 통째로 되돌아갔으니 다시 보내면 된다(학습 화면은 CONFLICT면 재시도를 막지 않는다).

**결정 3 — 일괄 제출에서 지워진 카드는 건너뛴다**
- 세션 도중 카드를 지우면 그 카드 하나 때문에 제출이 매번 404 → 답안을 통째로 버려야 했다. 별표 일괄 설정(ADR-053 보완)처럼 건너뛰고 `skippedIds`로 알려 준다(화면: "지워진 카드 N장은 건너뛰었어요").
- total·known은 반영한 카드만, 지문은 받은 답안 전체(같은 초안 재전송은 같은 제출로). 남의 카드는 그대로 403(통째 롤백). 전부 지워졌으면 출석도 찍지 않는다.\n- 학습 결과 화면의 '카드만 훑어봄'은 화면이 직접 표시한다 — 서버 total 0으로 추측하면, 답한 카드가 전부 지워진 경우를 '답 없이 봤다'로 잘못 보인다(PR 전 검토).

**결정 4 — 입력 경계는 400 (500·200이 아니라)**
- 빈칸 규칙 하나(`ValidationPatterns.HAS_TEXT`): 눈에 보이는 글자가 하나라도 있어야 한다 — 공백(전각 U+3000·NBSP 포함)과 안 보이는 글자(폭 0 문자 등 서식 문자 Cf, 한글 채움 문자 ㅤ U+3164 등)만 있으면 빈칸. 만들 때(@NotBlank 옆)·수정 때(PATCH — 안 보낸 칸 null은 그대로)·가져오기 줄이 모두 같은 규칙. 처음엔 수정 때만 넣었다가, PR 전 검토가 '만들 때 통과한 값을 수정 때 거부하고 탐색엔 여전히 빈 제목이 보일 수 있다'고 짚어 하나로 묶었다. 카드 단어·뜻, 덱 제목.
- 비밀번호 72바이트(`PasswordLimit`): BCrypt encode()가 넘는 값에 예외 → 가입·비밀번호 변경이 500이었다. 바이트라서 영문 72자, 한글·일본어·중국어 24자. 로그인은 일부러 막지 않는다 — 8/25 전(Security 6.3.0)엔 encode()가 말없이 72바이트로 잘라 저장했고, 그때 긴 비밀번호로 가입한 사람은 지금도 그대로 로그인한다(matches()는 앞 72바이트로 비교).
- 가입 닉네임 1~30자(프로필 수정과 같게)·이메일 255자, 답안 목록의 null 원소(`List<@NotNull @Valid Item>`), 아주 큰 `?page=`(page×size가 int를 넘어 500 → 마지막 페이지 앞에서 멈춤), 스프링 MVC의 4xx(405·415·413)는 그 코드 그대로("지원하지 않는 요청입니다"/"요청이 너무 큽니다", 405의 Allow 헤더 유지).

**결정 5 — 글자 처리**
- BMP 밖 한자(𠮟 U+20B9F — 2010년부터 상용한자 표기, 𩸽 등): 정규식에 u 플래그 + 범위는 `\u` 표기(글자를 그대로 쓰면 NFC 정규화가 호환 한자 豈 F900을 8C48로 바꿔 범위가 한글까지 넓어진다). 시험지 읽기 칸·일본어 양식, 발음 언어 판별(`detectLang.ts`로 떼어 단위 검사).
- JWT의 이메일을 UTF-8로 읽는다(`jwt.ts`) — atob만 쓰면 한글 이메일이 깨져 탈퇴 확인이 늘 실패했다.
- 구글 닉네임 20칸 자르기가 이모지를 반으로 갈라 '?'로 저장했다 → 반쪽이면 19칸.
- 개인정보처리방침(한국어뿐) 링크에 일·영·중 화면은 "(한국어)" 표시.

**남긴 것:** 구글이 이름을 안 줄 때 기본 닉네임 '구글사용자'(한국어) — 이메일 앞부분으로 바꾸면 공개 덱 작성자 칸에 이메일 일부가 보여서 제품 결정으로 남긴다. privacy.html 번역본(법 문서).

**테스트:** 새 서버 테스트 25개 — UserOverwriteTest 3·CardOverwriteTest 4·ApiInputBoundaryTest 13·ReviewBatchAnswerTest 2·LockFailureMappingTest 1·GoogleLoginTest 1·ImportServiceTest 1. 지켜야 할 동작을 확인하는 2개(로그인 73바이트 → 401, 설명만 PATCH → 200)를 빼고 전부 고치기 전 코드에서 실패를 확인. 서버 321개, 화면 단위(시험지 54·i18n 열쇠 572·글자 처리 13) 통과. PR 전 검토(에이전트 3개, 읽기만, 약 83만 토큰)가 찾은 P3 2건(빈칸 규칙 불일치, 결과 화면 문구) 반영.

---

## ADR-058: 화면 틀 — 위쪽 바(덱 검색·만들기·언어·프로필) + 왼쪽은 메뉴 (Quizlet식, 8/30 사이드바 개정)

**상태:** 채택 (2026-11-14 → PR #35)
**배경:** 사용자 요청(퀴즐렛·KRDS 캡처) — 가운데 위에서 덱을 바로 검색, 검색 옆 +로 바로 만들기, 프로필은 오른쪽 위에서 깔끔하게, 언어는 위에서 고르기, 왼쪽은 메뉴만. 그림(목업)으로 배치를 먼저 맞춘 뒤 구현.

- **구조**: TopNav 하나가 위쪽 바(`.topbar`, 고정 64px · 폰 56px)와 사이드바(`.sidenav`, 메뉴·내 폴더·연속 학습)를 그린다. 학습 4모드(몰입)는 둘 다 없음. 본문 여백은 CSS `body:has(.topbar)`·`:has(.sidenav)`가 따라온다(페이지 무수정).
- **검색**: 새 화면 `/search?q=` — 내 덱(이미 한 번에 오는 목록을 화면에서 거름: 이름·설명) + 공개 덱(탐색과 같은 검색 API, 내 덱은 위에 있으니 뺌). 폰은 위쪽 바에 검색창 대신 돋보기 → 검색 화면의 큰 검색창.
- **+ 메뉴**: 새 덱 / 파일로 덱 만들기 / 새 폴더 — 덱 화면의 기존 칸·창을 그대로 연다(`/decks?new=deck|folder`). 덱 화면은 한 번 처리하고 주소에서 지운다(뒤로가기·새로고침에 다시 안 열리게, 같은 이동은 location.key로 한 번만).
- **프로필 메뉴**: 이름·이메일·가입 방식·가입일, 설정, 개인정보처리방침, 로그아웃. 바깥 클릭·Esc·화면 이동 때 닫힘.
- **언어**: 위쪽 바 오른쪽(🌐 + 고르기 상자, 로그인 화면은 그대로). 설정 화면의 상자는 뺐다 — 한 화면에 같은 'Language' 상자가 둘이면 헷갈리고, E2E도 하나를 집지 못한다. 설정엔 안내 문구.
- **폰**: 아래 탭바는 그대로 — 메뉴를 ☰ 안에 숨기면 두 번 눌러야 한다(처음 그림 설명의 ☰ 대신). 위쪽 바는 로고 마크·돋보기·+·언어·프로필.
- **테스트**: E2E `topbar.spec.ts` 4개(검색으로 덱 찾아 들어가기, + 새 덱 커서·새 폴더 만들기, 프로필 메뉴·Esc, 폰 돋보기·탭바), 기존 가로 넘침 4폭·언어별 화면 검사 그대로 통과(로컬 38개). 화면 캡처 4장(프로필 메뉴·+ 메뉴·검색 결과·폰)을 사용자에게 보여 줌.
- **보완 (11/14 → PR #36): 덱 화면 학습 버튼** — '복습 학습 (Leitner)'·'⭐ 별표 복습' 두 버튼을 **낱말카드** 하나로(누르면 **카드 학습**(`mode=browse` — 채점 없이 뒤집어 보기만, 알아요·몰라요·제출 없음, 아무것도 기록 안 함) / 전체 복습 / 별표만 복습 N장 — 별표가 없으면 비활성, 복습 경로·학습 로직은 그대로), 퀴즈·타이핑·듣기·시험지에 아이콘(물음표·키보드·헤드폰·문서). 아이콘·펼침 메뉴는 위쪽 바와 공용(`components/icons.tsx`, `lib/useDropdown.ts`). 사용자: 글자만으론 구분이 어렵다. 카드 학습은 사용자가 '순수하게 카드만 넘겨 보고 싶을 때도 있다'고 해서 더함 — 학습 화면에서 답 버튼·A/D 키를 빼고, 스와이프는 채점 대신 넘기기(왼쪽 = 다음, 오른쪽 = 이전), 초안 저장 칸도 따로(`.browse`). E2E: 낱말카드 메뉴 세 갈래 링크·별표만 복습 이동, 카드 학습은 채점 키를 무시하고 끝까지 넘겨도 `/reviews/` 요청 0.
- **보완 (11/14 → PR #37): 연관 검색·검색 결과 순서** — 위쪽 검색창에 연관 검색(SearchBox, Quizlet식): 비어 있으면 최근 학습한 덱(이 브라우저 기록 — 없으면 내 덱 앞쪽), 치는 동안 이름이 맞는 내 덱 + 공개 덱(탐색 검색 API, 250ms 멈췄을 때만 호출, 내 덱은 위 칸에 있어 뺌) + '모든 결과 보기'. ↑↓·Enter·Esc, 목록이 닫히면 고른 줄을 푼다(Esc 뒤 Enter가 안 보이는 줄을 열던 것 — 캡처 스크립트가 찾음). 검색 결과 화면은 사용자 피드백('검색은 보통 공개된 걸 찾는 것')대로 공개 덱 먼저·내 덱은 8개까지 접기. E2E 2개(↓·Enter로 덱 열기, 고른 뒤 Esc → Enter = 검색 화면).
- **보완 (11/14 → PR #39): Codex 4차 검산 3건** — ① 개발 서버(Vite) 프록시에 `/folders`·`/rankings`·`/users`가 빠져 `npm run dev`에서 404(배포 jar는 멀쩡) → 추가 + `scripts/test-proxy.mjs`(화면 코드가 부르는 API 주소 첫 칸이 전부 프록시에 있는지, 고치기 전 3개 잡음) ② 연관 검색이 고른 줄을 '번호'로 기억해 늦게 온 결과가 목록을 바꾸면 다른 덱이 열릴 수 있었다 → 줄의 주소로 기억 + 검색어가 바뀌면 지난 공개 덱 결과를 바로 치움(E2E: 응답을 붙잡아 두고 재현) ③ 탐색이 ?q=를 처음에만 읽어 메뉴 '탐색'으로 다시 와도 지난 검색이 남음 → 주소를 따라감.

---

## ADR-059: 학습 랭킹 — 답한 카드 1장 = 1점(오늘 / 전체), 모두 표시 + 닉네임 가리기

**상태:** 채택 (2026-11-14 → PR #38)
**배경:** 사용자 요청('단어 하나 학습할 때마다 1점, 오늘의 랭킹·총 랭킹'). 공개 방식은 사용자 결정 — **모두 표시하고, 설정에서 닉네임 가리기(qjw***)**. 랭킹엔 닉네임만 나가지만, 구글 가입자는 처음 닉네임이 구글 이름(실명일 수 있음)이라 가리기가 필요하다.

- **점수는 새로 쌓지 않는다**: ~~모든 채점 모드(복습·퀴즈·타이핑·듣기)가~~ **서버가 채점을 기록하는 모드(낱말카드 복습·퀴즈·타이핑)가**(11/15 정정 — 듣기·시험지는 화면에서만 채점해 서버 기록이 없다) 이미 `daily_user_stats.study_count`에 답변 수를 더한다(StatsService.recordStudy). 오늘 = 그날(KST) 줄, 전체 = 합계 — 지난 기록도 그대로 점수. 채점 없는 카드 학습은 0점.
- **가리기는 서버에서**: 화면에서 가리면 API 응답에 진짜 닉네임이 실려 간다. 앞 글자(길이의 1/3, 1~3글자, 코드 포인트 단위) + `***` — 별표는 늘 3개라 길이도 안 드러남(qjwkr15 → qjw***, 조현동 → 조***). 내 줄도 남들이 보는 그대로.
- **순위**: 같은 점수면 같은 순위(1·2·2·4), 내 순위 = 나보다 높은 사람 수 + 1(상위 50 밖이어도 알려 줌), 탈퇴 계정 제외.
- **저장·조회**: V22 `users.ranking_masked`(기본 false → **V23에서 기본 true**, 아래 보완) + 오늘 랭킹용 인덱스 `(stat_date, study_count)`. DB 집계로 바로 — Redis ZSET은 안 씀(점수 출처가 한 곳이라 이중 기록·정합성 부담만 생김. 사용자가 늘면 덱 랭킹처럼 ZSET 캐시를 얹는다).
- **화면**: 왼쪽 메뉴 '랭킹'(오늘/전체 탭, 내 줄 강조, 50등 밖이면 맨 아래에 내 줄), 홈 맨 아래 줄 세 번째 카드 '오늘의 랭킹'(내 순위 + 상위 3, '오늘 학습 활동' 옆 — 사용자와 그림으로 정함), 설정 '랭킹에서 닉네임 가리기'(누르면 바로 바뀌고 실패하면 되돌림).
- **테스트**: 서버 `StudyRankingServiceTest` 5(오늘·전체·같은 점수·탈퇴 제외·가리기 규칙) + `RankingHttpTest` 2(가린 닉네임이 응답 어디에도 없음, 400·401), E2E `ranking.spec.ts` 2(답하면 내 줄·홈 카드, 가리기 켜고 끄기) + 가로 넘침 검사에 /app/ranking.

### 보완 (11/15 → PR #40) — 닉네임은 기본 비공개, 공개는 직접 체크
- 사용자 결정으로 기본값을 뒤집었다: **기본 = 가림(qjw\*\*\*), 설정에서 '랭킹에 닉네임 공개'를 체크한 사람만 그대로.** 구글 가입자의 닉네임은 구글 이름(실명일 수 있음)이라, 본인이 고르기 전에 남에게 보이면 안 된다(같은 날 점검에서도 가장 높은 순위로 나왔다).
- **V23**: 칸 기본값을 TRUE로 바꾸고 기존 사용자도 전부 가림으로 맞춘다 — V22와 V23 사이에 '공개'를 직접 고른 사람은 없다(고르는 칸이 없었다). 칸 이름(`ranking_masked`)은 그대로 둔다: 이름을 바꾸면 배포가 실패해 이전 이미지로 되돌아갈 때 그 이미지가 칸을 못 찾는다. 엔티티 기본값도 true(새 가입자).
- 화면: 체크박스의 뜻이 뒤집혔다(체크 = 공개 = 서버 값 masked false). 랭킹 화면 아래 안내도 '닉네임은 기본으로 가려져요. 설정에서 공개할 수 있어요'.
- **닉네임 바꾸기**: 설정 '계정'에 입력 칸을 넣었다(`PATCH /users/me` — 서버 API는 있었는데 화면이 없었다). 공개 덱 작성자·랭킹에 보이는 이름인데 고칠 길이 없었다. 공백·안 보이는 글자뿐인 닉네임은 400(`HAS_TEXT`, 가입·수정 둘 다).
- **정정**: 위 '점수는 새로 쌓지 않는다'에서 듣기도 점수가 쌓인다고 적었던 것은 틀렸다. 듣기와 시험지는 화면에서만 채점해 서버에 기록이 없다 — 점수는 낱말카드 복습·퀴즈·타이핑에서만 쌓인다. 랭킹 화면 문구(4개 언어)와 `StudyRankingService` 설명도 고쳤다.
- 테스트: `RankingHttpTest`(가입 직후에는 가려져 있고, 공개로 바꾼 뒤에만 그대로), `ApiInputBoundaryTest` 공백뿐인 닉네임 400, E2E `ranking.spec.ts` 3(답하면 내 줄 / 기본 가림 → 체크하면 공개 / 닉네임을 바꾸면 프로필 메뉴에 바로).

### 보완 2 (11/16 → PR #41) — 하루 상한 1,000점, 듣기·시험지는 점수 없음으로 확정
- ADR-060의 '남긴 것' 세 가지에 사용자가 답했다: **① 점수에 하루 상한을 둔다 ② 듣기·시험지에는 점수를 주지 않는다 ③ README 화면 캡처를 새로 찍는다.**
- **왜 상한인가**: 답 1장 = 1점이고 상한이 없어, 큰 덱을 되풀이하거나 일괄 제출(요청 하나에 답 1000개, ADR-050)을 거듭 보내면 점수가 얼마든지 올랐다.
- **값 = 하루 1,000점**(`StudyRankingService.DAILY_CAP`): 운영 실측(11/16) — 사용자·날짜 24줄 가운데 가장 많이 답한 날이 568장이고 1,000장을 넘긴 날은 없다. 지금까지의 학습은 한 점도 깎이지 않고, 되풀이로 부풀리는 길만 막힌다(500으로 잡으면 실제 기록 하나가 깎인다).
- **읽을 때만 자른다**: `daily_user_stats.study_count`에는 답한 수가 그대로 쌓이고, 랭킹 쿼리만 `least(study_count, 상한)`으로 읽는다. 쓸 때 멈추면 통계 화면의 학습량까지 깎이고, 나중에 상한을 바꿔도 지난 기록을 되살릴 수 없다. 마이그레이션 없음 — 지난 날짜에도 같은 규칙이 바로 적용된다.
- **전체 탭 = 날마다 자른 뒤 합계**(`sum(least(...))`). 합계를 한 번 자르면 여러 날 꾸준히 한 사람과 하루에 몰아서 한 사람이 같아진다.
- **상한에 닿은 사람끼리**는 같은 점수 → 같은 순위, 줄 순서는 가입 순(원래 규칙). 정렬도 자른 값으로 한다 — 답한 수로 세우면 '누가 더 많이 답했는지'가 순서로 드러난다.
- **내 순위**(목록 밖일 때 따로 세는 쿼리 둘)도 같은 식. 내 전체 점수는 통계의 누적 학습 수(`aggregate`, 자르지 않음)를 빌려 쓰던 것을 전용 쿼리(`cappedTotal`)로 갈랐다.
- **화면**: 랭킹 규칙 문구 끝에 '하루 최대 1,000점'(4개 언어). 숫자를 화면에 적어 두지 않고 응답의 `dailyCap`을 그대로 쓴다.
- **듣기·시험지**: 코드는 그대로다(화면에서만 채점해 서버 기록이 없다 → 0점). 결정이 된 만큼 E2E로 박아 둔다 — 듣기와 온라인 시험지를 채점한 앞뒤로 내 점수가 같다.
- **고려한 대안**: 같은 카드는 하루 한 번만 점수 — 뜻은 더 정확하지만 카드·날짜별 기록 표와 답마다의 쓰기가 새로 필요하다. 사용자 4명 규모에서는 상한 하나로 충분하고, 필요해지면 그때 얹는다.
- **테스트**: 서버 `StudyRankingServiceTest` 7(+2: 오늘 탭 — 상한·동점 순서, 전체 탭 — 날마다 자르기), `RankingHttpTest`(응답의 `dailyCap`, 800만 장 기록이 1,000점). 고치기 전 코드에서 3,007점·5,010점·800만 점으로 실패하는 것을 먼저 봤다. 랭킹 테스트는 900만대 점수로 맨 위에 두던 방식을 쓸 수 없게 돼, 테스트 트랜잭션 안에서 통계 표를 비우고 시작한다(롤백으로 되돌아간다). E2E `ranking.spec.ts` +1(듣기·시험지 점수 없음) + 상한 문구를 서버 값과 대조(옛 빌드에서 실패 확인). 서버 336 · E2E 73.
- **README 캡처**: 8월 캡처 4장(카드 1장짜리 덱) → 8장(홈·통계·덱·시험지·퀴즈·랭킹 + 폰 화면 일본어·영어). 로컬 빌드에 예시 계정·덱 3개·28일 학습 기록·랭킹 사용자 7명을 넣고 Playwright로 찍었다 — 운영 데이터와 실제 닉네임은 쓰지 않았다. 찍는 스크립트는 예시 데이터를 DB에 직접 넣는 일회용이라 커밋하지 않는다.

---

## ADR-060: 최종 점검 (11/15) — 얼려 둔 코드를 영역별로 읽고, 실제로 확인된 것만 고친다

**상태:** 채택 (2026-11-15 → PR #40)
**배경:** 11/14 하루에 위쪽 바·연관 검색·낱말카드 메뉴·랭킹·다국어를 몰아서 냈다. 사용자 요청('전체적으로 이상한 건 없는지 마지막으로 체크').

### 방법
- master(57e3a3a)를 별도 워크트리로 얼려 두고, 읽기 전용 에이전트 4개가 영역을 나눠 읽었다: 서버 / 화면 동작 / 화면 배치·키보드 / 남은 것·문서. 발견 42건(서로 겹치는 것 포함 — P1 2·P2 8·P3 32. P1 둘은 같은 건: 랭킹 닉네임이 기본 공개).
- 발견은 그대로 믿지 않고 하나씩 코드나 실제 브라우저로 다시 확인했다. 화면 배치는 에이전트가 글꼴 너비로 **계산**한 것이라 Playwright로 **쟀다** — 계산은 대체로 맞았고, 한 건은 실제가 더 나빴다(아래 폴더 담기 창: 에이전트는 '사파리·파이어폭스만'이라 했지만 크롬도 같았다).
- 화면 수리는 테스트를 먼저 쓰고, **고치기 전 빌드에서 실패하는 것을 본 뒤** 고쳤다(새 화면 테스트 중 11건).

### 고친 것 — 데이터가 잘못되거나 사라지던 것
- **덱 → 덱 이동 뒤 저장이 다른 덱을 덮어씀**: 덱 화면에서 '제목·설명 수정' 칸을 연 채 위쪽 검색으로 다른 덱으로 가면, 같은 Route라 React가 화면을 그대로 써서 앞 덱의 수정 칸이 남았다. '저장'을 누르면 **뒷덱의 제목·설명이 앞 덱 것으로 바뀌었다.** → 덱 id를 `key`로 줘서 덱이 바뀌면 화면을 새로 만든다. 공개 덱 화면도 같은 이유('복사됨' 상태가 남아 두 번째 덱을 복사할 수 없었다).
- **복습 답이 1000개를 넘으면 제출 불가**: 서버는 한 요청에 1000개까지만 받는데 화면이 통째로 보내 400, '다시 보내기'도 계속 400이라 답을 전부 버려야 했다. → 화면이 1000개씩 나눠 차례로 보낸다(`lib/submitChunks.ts`). 묶음과 묶음 ID는 얼려 둔 답안에서 늘 같게 나와서, 중간에 끊겨 다시 보내면 이미 받은 묶음은 서버가 '이미 제출됨'으로 답한다(ADR-050의 멱등 제출 그대로). 1000개 이하면 예전과 똑같은 요청 하나.

### 고친 것 — 사실과 다르던 글
- 랭킹 '듣기도 1점' → 듣기·시험지는 0점(ADR-059 정정).
- 개인정보처리방침: 저장 위치가 'Oracle Cloud Tokyo'로 남아 있었다(9/30 AWS 서울로 이전). 랭킹·공개 덱에 닉네임이 보인다는 내용과 개정일을 넣었다.
- README: 호스팅, ADR·테스트 수, Flyway 범위, 화면 수.

### 고친 것 — 화면이 깨지던 것 (실제 브라우저로 잰 값)
- **폴더 담기 창이 화면이 아니라 본문 칸 안에 떴다**: 본문 칸(`.shell`)의 등장 애니메이션이 `both`(끝난 뒤에도 마지막 값을 붙잡음)라 끝난 뒤에도 `transform`이 `none`이 아닌 값(제자리 행렬)으로 남았고, 그 칸이 안쪽 `position: fixed` 요소의 기준이 됐다. 어두운 막이 위쪽 바·사이드바·아래 탭바를 덮지 못했고, 덱 14개짜리 폴더를 폰(375×667)에서 열면 막이 **높이 2939px**로 잡혀 창이 그 한가운데(화면 밖)에 떴다. 애니메이션은 처음부터 있었으니 폴더 기능(8/30)을 낸 날부터 있던 문제다. → `both`를 뺐다(끝 값이 원래 모습과 같아 보이는 차이 없음) + 창을 본문 칸 밖으로 옮겼다(시험지 창과 같은 자리) + 막의 순서를 위쪽 바보다 위로.
- **폰 아래 탭바**: 랭킹 탭이 생겨 6칸이 된 뒤, 일본어 320px와 영어 320·360px에서 글자가 두 줄로 깨졌고(ホー/ム) 영어 320px는 '설정' 탭이 화면 밖으로 28px 나갔다. → 탭을 같은 너비로 나누고 글자는 한 줄.
- **프로필 메뉴·홈 랭킹 카드**: 일본어·영어에서 배지 글자와 카드 제목이 두 줄로 깨졌다. → 넘치면 뒤 요소(가입일·링크)를 다음 줄로.
- **낱말카드 메뉴**: 버튼이 오른쪽 끝에 몰리면(좁은 창·긴 제목) 메뉴 오른쪽이 화면 밖으로 나갔다. → 열 때 자리를 재서 모자라면 왼쪽으로 편다. 순서는 위쪽 바보다 아래, 사이드바·탭바보다 위.
- 덱 설명에 띄어쓰기 없는 긴 주소가 있으면 덱 목록이 가로로 32px 넘쳤다(820px). 낮은 화면에서 창의 아래 버튼이 창 밖으로 나갔다. 랭킹의 '나' 표시가 긴 닉네임의 말줄임에 같이 잘렸다. 연관 검색·메뉴의 12px 회색 글씨가 흐렸다(대비 3.4:1 → 4.9:1).

### 고친 것 — 화면 동작
- 연관 검색: 마우스가 올라가 있기만 한 줄이 Enter의 행선지가 됐다 / 화살표로 고른 줄이 목록 스크롤 밖으로 나갔다 / 내 덱 목록을 한 번만 받아 낡았다 / 공개 덱을 10개만 받아 내 덱을 빼면 비었다 / '최근 학습' 제목이 실제 목록과 달랐다.
- 펼침 메뉴: Tab으로 나가도 안 닫혀 두 메뉴가 겹쳐 열렸다 / 목적지가 지금 화면이면 안 닫혔다 / `role="menu"`인데 ↑↓ 키가 안 먹고 Esc로 닫으면 포커스가 사라졌다 → `useDropdown` 한 곳에서 처리. Tab으로 옮긴 포커스가 고정된 위쪽 바 밑에 숨던 것은 `scroll-padding-top`.
- 검색 화면: 한 번 실패하면 같은 검색어로는 다시 요청이 안 갔다. 탐색 화면: 화면 안에서 한 검색이 주소에 안 남아 뒤로 가면 옛 검색어로 돌아갔다.
- 카드 학습: 끝 화면의 문구가 채점 복습용이었고 덱으로 돌아갈 길이 없었으며, 홈 '이어하기'가 채점 복습으로 열렸다.
- 치운 것: Vite 기본 파비콘(→ 앱 로고), 안 쓰는 템플릿 파일·CSS·'공사 중' 화면, 옛 이름(복습 학습·별표 복습)과 죽은 사전 열쇠, 색이 없어 브라우저 기본 파랑으로 보이던 링크.

### 남긴 것 (결정 대기)
- **랭킹 점수 부풀리기**: 답 1장 = 1점이고 상한이 없어, 큰 덱을 되풀이해 제출하면 점수가 얼마든지 오른다. 하루 상한을 둘지는 사용자 결정. → **11/16 결정: 하루 1,000점 상한 (ADR-059 보완 2)**
- 듣기·시험지에도 점수를 줄지(지금은 서버 기록이 없어 0점). → **11/16 결정: 주지 않는다 (ADR-059 보완 2)**
- README의 화면 캡처는 8월 것 그대로다 → **11/16 새로 찍음**. 구글 가입자의 처음 닉네임, 개인정보처리방침 번역은 그대로 결정 대기.
- 파이어폭스에서는 재지 못했다(로컬 브라우저가 뜨지 않음). 폴더 담기 창은 크롬·웹킷(사파리 엔진)에서 고치기 전·후를 쟀다.

### 배포 전 검토 (같은 PR)
- 고친 뒤의 변경분을 읽기 전용 에이전트 2개(서버·데이터 / 화면)가 한 번 더 읽었다. 큰 문제는 없었고 4건(모두 P3)을 같은 PR에서 고쳤다.
- **구글 가입**: 구글 이름이 보이지 않는 글자뿐이면 기본 닉네임으로(`ValidationPatterns.hasText`, 20칸으로 자른 뒤에 본다). 빈칸 규칙을 가입·닉네임 수정에만 넣었고 구글 가입은 `isBlank()`라 한글 채움 문자·폭 0 문자가 통과했다.
- **듣기**: 별표 요청이 실패했거나 아직 안 끝난 채로 새 시험을 시작하면 지난 회차의 오류 문구·'별표 다시 시도'가 새 시험에 남았다 → 시험 회차 번호로 가르고, 시작할 때 오류를 지운다 (E2E로 고치기 전 실패 확인).
- **테스트 보강**: 낱말카드 '몰라요'가 별표를 켜지 않는다는 테스트가 일괄 제출 길만 봤다 → 한 장씩 답하는 두 길(`ReviewService.recordAnswer`, 옛 학습 세션)도 본다. 닉네임 테스트는 프로필 메뉴를 먼저 한 번 열어, 이름을 바꾸면 메뉴가 기억해 둔 프로필을 버리는 길을 실제로 지나게 했다.
- 확인만 하고 넘어간 것: V23은 MySQL 8에서 메타데이터 변경 + 작은 UPDATE이고, 배포가 실패해 이전 이미지가 다시 떠도 Flyway 기본값(모르는 뒤 버전은 무시)이라 뜬다 / 자동 ⭐는 세션 주인 확인 뒤에만 닿고 세션은 덱 주인만 시작한다 / 묶음 ID의 `~`는 원래 ID에 나올 수 없어 겹치지 않는다 / 사전 3개의 열쇠·자리표시 일치.

### 보완 (11/16 → 같은 PR) — Codex 5차 검산 2건 + 같은 종류 2곳
- **개발 서버에서 구글 로그인 콜백 404**: Vite 프록시가 `changeOrigin: false`라 백엔드는 제 주소를 5173으로 알고 구글에도 5173의 콜백(`/login/oauth2/code/google`)을 준다. 그런데 프록시 목록에는 로그인을 시작하는 `/oauth2`만 있고 돌아오는 경로가 없어, 구글에서 돌아오는 순간 404였다(배포 jar는 서버가 하나라 멀쩡). → `/login/oauth2` 추가. 같은 이유로 로그인·설정·프로필 메뉴의 `/privacy.html` 링크도 개발 서버에서 404였다 → 추가.
- `scripts/test-proxy.mjs`가 API 주소의 첫 칸만 보던 것을 넓혔다: 화면이 통째로 옮겨 가는 백엔드 주소(`<a href>`·`location.href`)와, 구글 로그인을 시작하는 주소가 있으면 그 콜백까지. 고치기 전 설정에서 실패하는 것을 먼저 봤다. 구글 콘솔에 5173 콜백 주소가 등록돼 있어야 개발 서버에서 로그인이 끝까지 되는데, 그건 코드 밖이라 확인하지 못했다.
- **닉네임 저장 중에 친 글자가 사라짐**: 'A 저장 → 응답을 기다리는 동안 B 입력 → A 응답 도착'이면 칸이 A로 되돌아갔다. → 응답이 왔을 때 칸의 글자가 보낸 값 그대로일 때만 칸을 바꾸고 '바꿨어요'를 띄운다(달라졌으면 '저장' 버튼이 다시 켜진다).
- **같은 모양을 찾아 2곳 더**: 덱 화면의 카드 추가 줄과 내 덱의 새 덱 이름 칸도 응답이 오면 칸을 통째로 비웠다 — 느린 망에서 응답 전에 다음 카드·다음 이름을 치기 시작하면 그 글자가 지워진다. → 보낸 값 그대로인 칸만 비운다. 비밀번호 변경은 그대로 둔다(성공하면 비우는 쪽이 안전하다). 수정 칸(덱 제목·카드)은 저장하면 칸이 닫히니 해당 없음.
- 테스트: E2E 2건(닉네임·카드 추가 — 응답을 붙잡아 두고 그사이 입력, 고치기 전 빌드에서 실패 확인), `test-proxy.mjs` 확장.

### 테스트
- 서버 334개 통과. 화면 단위 검사에 `test-submit.mjs` 11개(묶음 나누기).
- E2E 72개 통과 — 새로 넣은 것: 응답 대기 중 입력 2(닉네임·카드 추가), 자동 ⭐ 4(듣기 회차 포함), 1000개 넘는 제출·카드 학습 끝 2, 랭킹 기본 가림·닉네임 바꾸기 2, 연관 검색(마우스·Tab) 2, 덱 → 덱 이동 1, 메뉴 키보드 1, 낱말카드 메뉴 6(폭·제목 길이별), **폰 고정 바 4(언어별 — 고정 요소는 가로 넘침 검사에 안 잡혀 직접 잰다)**, 프로필 메뉴·랭킹 카드 줄바꿈 2, 폴더 담기 창 1, 긴 설명 넘침(기존 테스트에 추가).
- 연관 검색 테스트의 '첫 줄' 고르기가 내 덱 목록 도착 전에 '모든 결과 보기' 줄에 걸리던 것을 '덱 줄만' 고르게 고쳤다(그 줄에도 검색어가 들어 있다).

---

## ADR-061: 연결 풀 교착 — 커밋하면 연결을 바로 반납한다 (Codex 7차 검산)

**상태:** 채택 (2026-11-16 → PR #42)
**범위:** `application.yml`(main·test)의 `hibernate.connection.handling_mode`, `DeckStudyRankingListener` 설명, `ConnectionPoolDeadlockTest`, E2E `concurrency.spec.ts`, `index.css`의 `.voice-select`

### 문제 (P1)
- 답을 저장하는 요청 36개를 한꺼번에 보내면 25개가 약 30초 뒤 401·500으로 돌아왔고, 그 30초 동안 로그인을 포함해 DB를 쓰는 요청 전부가 멈췄다 (Codex 재현: 방금 가입한 계정 하나로 36개 동시 요청).
- **원인**: 학습 기록이 커밋되면 `DeckStudyRankingListener`(AFTER_COMMIT + REQUIRES_NEW — ADR-038·039에서 '동기'로 정한 것)가 새 트랜잭션을 연다. 그런데 스프링이 Hibernate에 거는 기본값(`DELAYED_ACQUISITION_AND_HOLD`)은 커밋 뒤에도 EntityManager를 닫을 때까지, 곧 AFTER_COMMIT 콜백이 다 끝날 때까지 원래 연결을 쥐고 있다. 그래서 리스너의 새 트랜잭션은 **연결을 쥔 채 하나를 더** 요구한다.
- 풀은 기본 10개다. 요청 10개가 동시에 그 자리에 오면 10개가 전부 '쥐고 기다리는' 상태가 되고, 남는 연결이 없으니 아무도 진행하지 못한다. HikariCP의 대기 시간(30초)이 지나 누군가 포기해야 풀린다. 그동안 DB는 한가했다(연결 10개 모두 Sleep) — 락도 느린 쿼리도 아닌, 풀 안에서의 교착이다.
- 8/22(ADR-038)부터 있던 구조다. 한 사람이 답을 하나씩 보내는 평소에는 드러나지 않지만, 가입만 하면 누구나 요청을 몰아 보내 서비스 전체를 30초씩 멈출 수 있었다.
- 저장된 답은 유실되지 않았다: 리스너가 도는 시점에 답은 이미 커밋돼 있다. 잃는 것은 그 요청의 인기 점수 +1과, 연결을 얻지 못해 실패한 다른 요청들이다.

### 결정
- **커밋하면 연결을 바로 풀에 돌려준다**: `spring.jpa.properties.hibernate.connection.handling_mode = DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION`. Hibernate 자체의 기본값인데 스프링이 HOLD로 덮어쓰던 것을 되돌린 것이다. 연결이 커밋 안에서 반납되므로, AFTER_COMMIT 리스너가 새 연결을 받을 때 그 스레드는 아무것도 쥐고 있지 않다 — **한 스레드가 연결을 두 개 쥐는 구간이 없어진다.**
- 리스너는 그대로 동기다(ADR-039의 '원본 기록은 동기' 유지). 실행 순서·잠금 순서(ADR-031)·하루 1회 규칙도 그대로.
- **잃는 것**: 스프링이 연결에 직접 거는 설정(트랜잭션별 격리 수준, `Connection.setReadOnly`)을 쓸 수 없다. 이 코드에는 격리 수준 지정이 없고(넣으면 트랜잭션을 시작할 때 예외로 바로 드러난다), 읽기 전용 트랜잭션은 Hibernate가 자동 flush를 끄는 것으로 계속 동작한다. DB가 '읽기 전용 연결에서의 쓰기'를 막아 주던 안전망은 없어진다.
- 트랜잭션 **안**에서 REQUIRES_NEW를 여는 코드를 새로 넣으면 같은 문제가 돌아온다(그때는 바깥 트랜잭션이 연결을 쥐고 있는 것이 정상이라 이 설정으로 막을 수 없다). 지금은 그런 코드가 없다.

### 고려한 대안
- **풀을 키운다** — 같은 모양의 동시 요청이 풀 크기에 닿으면 다시 생긴다. 교착이 없으려면 '동시에 처리하는 요청 수 + 1'개가 필요한데 톰캣 기본 스레드가 200개다. 거부.
- **리스너를 @Async로** — 요청 스레드는 연결을 놓지만, 큐가 차면 CallerRuns(ADR-039)가 같은 자리에서 직접 실행해 문제가 돌아오고, 버리는 정책으로 바꾸면 원본 기록이 사라진다. ADR-039에서 동기로 정한 이유 그대로 거부.
- **같은 트랜잭션에서 처리(BEFORE_COMMIT)** — 연결은 하나로 끝나지만, 인기 점수 쪽의 잠금 대기·교착이 학습 기록까지 롤백시킨다(지금은 인기 점수만 잃는다). 거부.
- **Outbox** — 유실 0 + 비동기지만 표·워커·재시도가 딸린 작은 시스템이다(ADR-039의 (C)). 지금 규모에는 과하다.

### 테스트 (모두 고치기 전에 실패하는 것을 먼저 봤다)
- 서버 `ConnectionPoolDeadlockTest`: 풀을 3개로 줄이고, 학습자 셋이 모두 '커밋 직전(연결을 하나씩 쥔 채)'에 모인 뒤 함께 커밋하게 한다. 고치기 전 = `total=3, active=3, idle=0`으로 2초 대기 뒤 셋 다 인기 점수 반영 실패(0/3), 고친 뒤 = 3/3. 테스트 쪽 `application.yml`은 main과 파일이 달라 같은 값을 따로 넣었다.
- E2E `concurrency.spec.ts`: 실제 jar(운영과 같은 `application.yml`, 풀 10개)에 답 30개를 한꺼번에 보낸다. 고치기 전 = 31초, 30개 중 19개가 401·500(`total=10, active=10, idle=0, waiting=28`), 고친 뒤 = 1.7초, 전부 200.
- 서버 337 · E2E 74.

### P3 — 파이어폭스 설정 화면 넘침 (같은 PR)
- 파이어폭스의 `<select>`는 flex 안에서 가장 긴 항목보다 좁아지지 않는다(`min-width: auto`). 음성 이름이 길면(Microsoft … - English (United States)) 375px 화면에서 오른쪽으로 22px 밀려나 화면 전체가 가로로 움직였다. → `.voice-select { min-width: 0 }`. 고친 뒤 파이어폭스도 크롬과 같은 배치가 된다(문서 폭 375 = 화면 폭, 선택 상자 97px·크롬 95px).
- CI의 화면 테스트는 크롬뿐이라 이 문제는 자동으로 잡히지 않는다. 로컬 파이어폭스로 고치기 전(문서 폭 397px)·후(375px)를 쟀다 — 파이어폭스는 `setViewportSize(375)`에서 멈추므로 처음부터 375px로 연다(Codex의 방법).

### 남긴 것
- **전체 랭킹의 집계 비용**(Codex 측정, 로컬): 통계 100만 줄(1만 명 × 100일)에서 전체 탭 한 번에 중앙값 8.9초, 동시 8개면 31번 중 13번이 30초를 넘겼다. 목록 집계와 '내 순위' 집계가 요청마다 전부를 다시 더하기 때문이다. 3만 줄에서는 0.14초, 운영은 24줄이다. ADR-059에 적은 대로 사용자가 늘면 합계를 따로 들고 있거나(표·ZSET) 캐시를 얹는다 — 지금은 손대지 않는다.
- DB 연결을 못 얻으면 인증 단계의 사용자 조회가 실패해 **401로 보인다**(실제 토큰 문제가 아니다). 이번 수리로 그 상황이 생기는 길 하나를 막았지만, DB 장애 때 401이 맞는 응답인지는 따로 볼 일이다.
- 파이어폭스·웹킷 전체 화면 테스트는 Codex 쪽 실행에서 각각 57/73·70/73이었다. 실패는 자동화 도구가 375px로 크기를 바꾸다 멈춘 것, 화면 이동 중 글꼴 요청이 취소된 것, 웹킷에 음성 기능이 없는 것으로, 제품 문제로 확인된 것은 위 P3 하나다.

---

## ADR-062: 채점 — 띄어쓰기가 달라도, 뜻을 순서 바꿔 여러 개 적어도 정답 (ADR-026·051 개정)

**상태:** 채택 (2026-11-18 → PR #43)
**범위:** `common/AnswerMatcher.java`, `frontend/src/lib/answerMatch.ts`, 공유 사례표 `answer-match-cases.json`, `docs/typing-policy.md`, E2E `online-test.spec.ts`

### 문제
- 11/18 사용자가 어셔 토플 day13(246문항)을 온라인 시험으로 풀었다. 앱 점수 47점. 답을 적은 105칸 가운데 58칸이 오답 처리됐는데, 사람이 다시 보니 그중 38칸은 뜻이 맞았다. 사용자: "띄어쓰기, 그리고 뜻을 앞뒤 섞어서 적어도 맞게끔 채점해야지 지금은 채점이 열받는 방식".
- 맞는데 오답이 된 38칸을 나누면 — 띄어쓰기만 다른 것 5칸(`피할수없는` ← `피할 수 없는`, `원시시대의`, `설득력있는`, `깜짝놀라게하다`, `구식이 되게하다`), 카드에 있는 뜻 둘을 쉼표로 적은 것 5칸(`끈기,고집` ← `n. 고집, 끈기`, `정적인,고정된`, `챔피언,옹호하다`, `협곡,실컷먹다`, `짜증나는,성가신`), 나머지 28칸은 비슷한 말·활용형·품사 차이(`꾸준하게` ← `꾸준히`).
- 앞의 10칸은 예전에 일부러 오답으로 정한 것이다: ADR-026 "단어 중간 띄어쓰기는 그대로", "쉼표로 묶인 답을 통째로 친 것은 후보가 아님". 뜻→단어 방향의 철자 확인을 생각하고 정한 규칙이 단어→뜻 방향(한국어 뜻)에서는 아는 답을 틀렸다고 했다.

### 결정
1. **한글·가나·한자가 든 후보는 띄어쓰기를 빼고도 비교한다.** `피할수없는` = `피할 수 없는`, 입력 쪽에 띄어쓰기가 더 있어도 같다(`회 의` = `회의`). **로마자뿐인 후보는 예전처럼 띄어쓰기까지 본다** — `app le ≠ apple`, `icecream ≠ ice cream`. 영어는 띄어쓰기도 철자라서 ADR-026의 판단을 그대로 둔다.
2. **입력에 뜻을 여러 개 적었으면**(`, ， ; ； / ／ ・`로 나눈다) **조각이 전부 후보일 때 정답**이다. 순서는 상관없다(`능금, 사과` ← `사과, 능금`). 후보에 없는 뜻이 하나라도 섞이면 오답(`사과, 포도`). 끝에 남은 쉼표는 없는 것으로 보고, 구분자만 친 입력은 오답이다.
3. 입력 전체가 후보 하나와 같은지를 **먼저** 본다 — 예전에 정답이던 입력은 계속 정답이다.
4. 타이핑(서버)·듣기·온라인 시험(화면) 모두 같은 규칙이다(ADR-051의 '두 구현 + 같은 사례표' 유지). 온라인 시험의 읽기 칸도 같은 함수라 `つの／かど` ← `かど／つの`가 정답이 된다.

### 고려한 대안
- **조각 중 하나만 맞아도 정답** — `더하다, 이상한`(odd)처럼 틀린 뜻을 섞어 적어도 정답이 된다. 사용자가 같은 날 "앞으로는 시험으로 맞춰야만 정답으로 인정"하겠다고 했으니, 시험이 틀린 연결을 통과시키면 안 된다. 거부.
- **띄어쓰기를 모든 답에서 무시** — 뜻→단어 방향에서 `alot`을 `a lot`의 정답으로 받는다. 거부.
- **온라인 시험에만 적용** — 같은 뜻 칸을 타이핑 모드와 다르게 채점하게 된다(ADR-051이 없앤 차이). 거부.
- **비슷한 말·활용형까지 자동 인정** — 사전이나 형태소 분석이 필요하고, 틀린 답을 맞혀 줄 위험이 크다. 지금처럼 온라인 시험의 '맞음으로'로 사람이 정한다.

### 테스트 (고치기 전 코드로 먼저 돌려 봤다)
- 공유 사례표 59 → 76개. 뒤집힌 것 3개(`사과, 능금` 통째로, `눈에띄다`, `회의` ← 넓은 공백이 든 `회 의`: 오답 → 정답), 새로 넣은 것 17개(순서·구분자·빈 조각·섞인 오답·로마자 유지·11/18 실제 답).
- 새 사례표에서 고치기 전 코드는 15개 실패, 고친 코드는 0개. 예전 사례표에서 정답이던 입력이 오답으로 바뀐 것은 0개.
- 11/18 실제 답 17개를 두 코드로 채점: 위 10칸은 오답 → 정답, `명백한,명확한`·`더하다,이상한,기묘한,홀수`·`수축,위약`·`치다,파업`·`자비심있는,배려하다`·`꾸준하게`·`구별하다` 7칸은 그대로 오답.
- 서버 `AnswerMatcherTest` 80개, 화면 단위 검사 `answerMatch` 93개 통과. E2E `online-test.spec.ts`에 한 개 추가(띄어쓰기 없이·순서 바꿔 = 정답, 없는 뜻을 섞으면 오답이고 ⭐).

### 남긴 것
- 답 자체에 쉼표가 든 카드(`Hello, World`)는 예전부터 조각으로 갈라진다(typing-policy §4) — 그대로다.
- 비슷한 말·활용형·품사 차이는 자동으로 잡지 않는다. 위 38칸 가운데 28칸은 이 수정 뒤에도 '맞음으로'가 필요하다.
- 덱 화면에서 별표를 누르면 카드 목록 전체를 다시 불러온다(`DeckDetail.toggleStar` → `load()`). 11/18 별표 74개를 화면에서 끄는 동안 목록이 다시 그려지며 스크롤이 맨 위로 돌아가는 것을 봤다. 이번 PR에서는 손대지 않았다. (→ PR #44에서 수리, ADR-063 끝)

### 보완 (Codex 8차 검산, 11/18 → PR #44)
배포 직후 Codex가 `e939cee`를 검산해 두 건을 찾았다. 둘 다 서버와 화면 구현에서 재현됐다.
- **P2 — 영어 철자 판정이 뜻 순서에 따라 달라짐**: `ice cream/아이스크림` 카드에 `icecream/아이스크림`은 정답, 순서만 바꾼 `아이스크림/icecream`은 오답이었다. 띄어쓰기를 빼고 비교할 후보를 **모든 후보**에서 골랐기 때문이다 — 쉼표로만 나눈 예전 후보(`ice cream/아이스크림` 통째)에 한글이 들어 있으니 영어 쪽 띄어쓰기까지 무시됐다. 결정 1의 "로마자뿐인 답은 그대로"가 구분자로 묶인 답에서는 지켜지지 않은 것이다. 타이핑 모드에서는 잘못 인정한 답으로 연속 정답 횟수까지 올라갔다. → 띄어쓰기를 빼고 비교하는 대상을 **낱개 뜻**(괄호·품사 표시를 걷어내고 모든 구분자로 나눈 조각)으로 좁혔다. 전체 일치는 그대로 먼저 본다. 괄호 설명에 한글이 든 `ice cream (아이스크림)`도 같은 모양이라 함께 막힌다.
- **P3 — 호환·확장 한자가 범위에서 빠짐**: 호환 한자(U+F900–FAFF)로 저장된 답(예: U+FA19·U+FA4C, 神社의 호환 코드)과 BMP 밖 확장 한자(예: U+20BB7)만으로 된 답은 띄어쓰기만 달라도 오답이었다. → 화면의 시험지 생성기(`testSheet.ts`의 KANJI)·언어 감지(`detectLang.ts`)와 같은 한자 범위를 쓴다(々〆, 확장 A, 통합, 호환, 20000–3FFFF).
- 사례표 76 → 84개. 배포돼 있던 코드(#43)는 새 사례표에서 4개를 틀리고(P2 2개·P3 2개), 고친 코드는 0개. #43에서 정답이던 입력 가운데 오답으로 바뀌는 것은 위 P2 입력 2개뿐이다.
- 놓친 이유: #43의 사례표에는 로마자 뜻과 한글 뜻이 **구분자로 묶인** 답이 없었다(`DNA 복제`처럼 한 뜻 안에 섞인 것만 있었다).

### 보완 2 (Codex 9차 검산, 11/18 → PR #45) — 입력에 적은 괄호 설명·품사 표시
- 위 보완(띄어쓰기 무시는 낱개 뜻마다)에 따라온 경계를 Codex가 짚었다. 카드 뜻이 `피할 수 없는 (불가피한)`일 때 `피할수없는`은 정답인데 `피할수없는 (불가피한)`은 오답, `a. 피할 수 없는`에 `a. 피할수없는`도 오답이었다. 둘 다 #43에서는 정답이던 입력이다 — 그때는 쉼표로만 나눈 예전 후보(`피할 수 없는 (불가피한)` 통째)도 띄어쓰기를 빼고 비교했기 때문이다. 버그라기보다 정한 적 없는 자리다: 입력에 적힌 괄호·품사 표시를 어떻게 볼지는 ADR-051도 062도 말하지 않았다(정답 칸의 것만 걷어냈다).
- 결정(사용자 "응, 다 고쳐"): **입력도 정답 칸과 같은 방법으로 걷어내고 본다.** 괄호 안은 설명으로 보고 지우고, 품사 표시(`n.` `a.` …)와 ①②는 구분자로 본 뒤, 남은 조각이 전부 후보면 정답이다. 판정 순서는 ① 입력 전체가 후보 ② 구분자로 나눈 조각이 전부 후보 ③ 걷어낸 조각이 전부 후보. ③은 앞의 둘이 안 맞을 때만 쓰는 마지막 단계라, 전에 정답이던 입력이 오답으로 바뀌는 일은 없다.
- 따라오는 것:
  - 입력의 괄호 안은 채점하지 않는다 — `사과 (포도)` ← `사과, 능금`은 정답이다(정답 칸의 괄호와 같은 대접).
  - 괄호 안에만 적으면 조각이 하나도 안 남아 오답이다(`(사과)`, 설명만 괄호째 친 `(사사로운 이익을 …)`). 닫지 않은 괄호는 설명으로 보지 않는다.
  - 카드에 없는 품사 표시를 붙여 적어도 뜻이 맞으면 정답이다(`n. 사과` ← `사과`).
  - 영어 뜻의 띄어쓰기는 걷어낸 뒤에도 철자다(`n. icecream` ← `ice cream/아이스크림`은 오답 — 보완 1 그대로).
- 사례표 84 → 95개. 고치기 전 코드는 새 사례 11개 가운데 7개를 틀리고(정답이어야 할 7개), 고친 코드는 0개. 예전 사례 84개의 기대값은 하나도 바꾸지 않았다.

---

## ADR-063: 온라인 시험 — 맞힌 단어는 예전부터 켜져 있던 ⭐도 끈다 (ADR-053 개정)

**상태:** 채택 (2026-11-18 → PR #44)
**범위:** `frontend/src/pages/OnlineTest.tsx`, 번역 3개(`i18n/ja·en·zh.ts`), E2E `online-test.spec.ts`. 서버는 주석만 — 별표 일괄 설정 API는 그대로다

### 문제
- ADR-053은 "채점하면 틀린 단어에 ⭐, '맞음으로'는 **이 시험이 켠 ⭐만** 끈다"로 정했다. 사용자가 다른 이유로 켜 둔 ⭐를 시험이 지우지 않게 하려던 것이다. 맞힌 단어의 ⭐는 아예 건드리지 않았다.
- 11/18 사용자가 어셔 토플 day13(246장)을 시험으로 풀었다. 그 덱은 시험 전에 이미 226장이 ⭐였고, 채점 뒤에는 235장이 됐다(새로 켠 별표 9개). 정답으로 본 85장 가운데 74장은 시험이 끝나도 ⭐가 남았다 — 자동 채점으로 맞힌 47장 중 36장, 뜻이 맞아 '맞음으로' 대상인 38장 전부(그중 '맞음으로'로 끌 수 있는 것은 이번 시험이 새로 켠 것뿐이었다). 사용자가 원한 결과("네가 봤을 때 정답이면 별 없음, 오답이면 별")를 만들려고 덱 화면에서 ⭐ 74개를 하나씩 꺼야 했다.
- 같은 날 사용자 방침: **"앞으로는 시험으로 맞춰야만 정답으로 인정"** — 낱말카드는 넘기기만 하고, 아는지 모르는지는 시험이 정한다. 그러면 ⭐는 '시험에서 아직 못 맞힌 단어'여야 한다.

### 결정
1. **채점하면 틀리거나 비운 단어는 ⭐를 켜고, 맞힌 단어는 ⭐를 끈다.** 시험 전부터 켜져 있던 ⭐도 끈다.
2. **'맞음으로'도 ⭐를 끈다**(예전부터 켜져 있던 것도). '다시 오답으로'는 다시 켠다.
3. '다시 풀기'에서 맞힌 단어는 앞 풀이가 켠 ⭐가 꺼진다.
4. 결과 줄에 끈 수를 보인다 — `맞힌 단어의 ⭐ N개 해제`. 서버가 실제로 껐다고 답한 카드만 세고, 풀이마다 새로 센다. 0개면 문구를 그리지 않고, 틀린 단어가 없으면 `틀린 N개 ⭐ 완료` 문구도 없다. (→ 끄기 요청이 실패했다가 다시 시도한 경우의 셈은 ADR-064 결정 4에서 보완)
5. '새로 켠 별표 N개'의 뜻은 그대로다(시험을 열 때는 ⭐가 아니었는데 이 시험 때문에 지금 ⭐인 카드). 원래 ⭐였던 카드를 맞음으로 → 다시 오답으로 되돌린 것은 세지 않는다.
6. 서버는 바꾸지 않는다. 별표 일괄 설정(`PATCH /decks/{id}/cards/star`)이 값 지정·멱등이라 끄기 요청을 다시 보내도 결과가 같다 — ADR-053 보완의 '별표 다시 시도'가 그대로 통한다. 켜기 → 끄기 순서로 보내고, 어느 쪽에서 실패했는지에 따라 오류 문구를 달리한다. 전부 맞힌 풀이에도 끄기 요청이 나가므로, 별표 줄(진행·실패·다시 시도)은 틀린 단어가 없어도 그린다.
7. 복습 일정(라이트너)은 여전히 건드리지 않는다.

### 고려한 대안
- **끌지 말지 고르는 설정** — 선택지가 늘고 기본값을 또 정해야 한다. 사용자가 방침을 분명히 했으니 한 가지 동작으로 둔다.
- **'맞음으로'만 예전 ⭐를 끄고, 자동 채점으로 맞힌 것은 그대로** — 11/18에 손으로 끈 74개 중 36개가 자동 채점으로 맞힌 단어였다. 문제의 절반이 남는다.
- **퀴즈·타이핑·듣기도 맞히면 ⭐ 끄기** — 퀴즈는 보기 중에 고르는 것이라 찍어서 맞힐 수 있고, 사용자가 말한 것은 '시험'이다. 이번에는 온라인 시험만 바꾼다.

### 잃는 것
- ⭐를 시험과 상관없는 '즐겨찾기'로 켜 둔 카드도 시험에서 맞히면 꺼진다. 시험은 사용자가 직접 열어서 채점할 때만 돌고, 꺼진 ⭐는 덱 화면에서 다시 켤 수 있다.

### 테스트
- E2E `online-test.spec.ts`: 첫 테스트를 새 동작으로 바꾸고(맞힌 단어의 예전 ⭐가 꺼짐, 맞음으로가 원래 ⭐도 끔, 다시 오답으로 되돌린 원래 ⭐는 '새로 켠 별표'로 세지 않음) 2개 추가(다시 풀기에서 맞히면 꺼짐, 전부 맞혔는데 끄기 요청이 실패하면 '별표 다시 시도'). 나머지 별표 회귀 테스트(응답 유실·지워진 카드·저장 중 잠금·501장)는 손대지 않았고 그대로 통과한다.
- 바꾼 테스트를 고치기 전 코드로 돌려 보지는 않았다. 예전 테스트는 같은 자리에서 반대(원래 ⭐는 남는다)를 확인하고 있었다.
- 로컬(빈 MySQL·Redis에 띄운 jar, Chromium): 이 변경만 넣은 상태에서 전체 77개 통과. 채점 보완·별표 스크롤 수리까지 넣은 최종본은 전체 78개 중 77개 통과 — 남은 1개는 새로 넣은 별표 스크롤 테스트가 화면이 자리 잡기 전에 위치를 잰 탓이었다(별표를 누르지 않아도 글꼴이 늦게 바뀌며 문서 높이가 줄어 44px 차이). 누르기 전·후를 따로 재 보니 스크롤과 단추 위치는 그대로였고, 자리 잡은 뒤에 재도록 테스트를 고쳐 그 파일을 3번 반복해 모두 통과했다. 화면 단위 검사 통과(번역 열쇠 616개 — 새 문구 4개), 서버 `AnswerMatcherTest` 88개.

### 같은 PR — 덱 화면에서 별표를 누르면 스크롤이 맨 위로 돌아가던 문제
- ADR-062 '남긴 것'에 적은 문제다. Codex 8차 검산도 재현했다(125장 덱의 110번째 카드에서 켤 때와 끌 때 모두).
- 원인: `DeckDetail.toggleStar`가 별표를 바꾼 뒤 덱과 카드 전체를 다시 불렀고(`load()`), 그동안 목록이 '불러오는 중' 문구로 바뀌어 문서 높이가 줄었다.
- 수리: 응답으로 온 그 카드의 값만 목록에 반영하고 다시 부르지 않는다. 별표 수('카드 N장 · 별표 M장', 낱말카드 메뉴의 '별표만 복습 (M장)')는 화면의 카드 목록에서 센다.
- E2E `deck.spec.ts`에 한 개 추가: 60장 덱의 맨 아래 카드에서 별표를 켜고 꺼도 스크롤 위치가 1px 안에서 같고, 별표 수와 서버 값이 맞다. 이 테스트도 고치기 전 코드로는 돌려 보지 않았다.
- (→ 이 수리가 만든 틈 두 가지와 결정 4의 해제 개수는 ADR-064에서 보완)

---

## ADR-064: 별표 쓰기가 끝난 뒤에 목록을 읽는다 — 화면과 서버의 별표가 어긋나던 네 가지 (Codex 9차 검산)

**상태:** 채택 (2026-11-18 → PR #45)
**범위:** `frontend/src/api/cards.ts`, `pages/OnlineTest.tsx`·`DeckDetail.tsx`·`Study.tsx`, E2E `online-test.spec.ts`·`deck.spec.ts`. 서버는 채점기만 바뀐다(ADR-062 보완 2). 번역 사전에서 안 쓰게 된 열쇠 1개 삭제

### 문제
PR #44 배포 뒤 Codex가 `cdc5ba5`를 검산해 네 가지를 재현했다. 저장된 값이 틀어지는 것은 없고, 넷 다 **화면이 서버와 다른 별표(또는 숫자·문구)를 보여 주는** 종류다.
1. **(P2) 채점 직후 저장 중에 '별표만 복습'을 누르면 옛 목록이 열린다.** 온라인 시험은 채점하면 별표를 켜는 요청과 끄는 요청을 차례로 보낸다(ADR-063). 그 사이에도 결과 화면의 복습 링크가 눌렸고, 복습 화면은 들어올 때 카드 목록을 한 번만 읽는다. 그래서 방금 맞힌 단어가 복습에 나오고 틀린 단어는 빠졌으며, 저장이 끝나도 새로 고치기 전에는 그대로였다. 틀린 단어가 빠지는 쪽은 ADR-053부터 있던 틈인데, ADR-063으로 맞힌 단어의 ⭐를 끄게 되면서 눈에 띄게 됐다.
2. **(P2) 덱 화면에서 같은 별표를 빠르게 두 번 누르면 화면만 켜진 채 남는다.** 별표 토글(`PATCH /cards/{id}/star`)은 누를 때마다 값이 뒤집힌다. 요청 둘이 겹치면 서버는 켜기 → 끄기로 끝나는데, 첫 응답(켜짐)이 둘째보다 늦게 도착하면 화면은 켜짐으로 끝난다 — '별표만 복습 (1장)'까지 눌리게 된다. 별표 요청이 서버에 닿기 전에 목록을 다시 읽을 때(카드 추가 등)는 반대로 어긋났다(서버 ⭐, 화면 꺼짐). PR #44에서 별표를 누를 때 목록을 다시 부르지 않게 바꾸면서 생긴 틈이다 — 그 전에는 응답마다 목록을 다시 읽어 마지막 읽기가 이겼다(대신 스크롤이 튀었다).
3. **(P3) 끈 별표 수가 덜 나온다.** 별표 501개를 끄다가 둘째 묶음(마지막 1장)만 실패하면, 다시 시도 뒤 실제로는 501개가 다 꺼졌는데 '1개 해제'로 나왔다. 500장씩 나눠 보내는 `setStarred`는 중간에 실패하면 앞 묶음의 결과를 돌려주지 못하고, 다시 보낸 요청에서 그 카드들은 "바뀐 것 없음"이다. 끄기 응답만 끊긴 경우(서버에는 반영)에는 해제 문구가 아예 안 나왔다(Codex 자료 `lost_response_after_commit`).
4. **(P3) 덱 화면에서 별표 요청이 실패해 뜬 오류 문구가, 다시 눌러 성공해도 남는다.** 이것도 #44 전에는 목록을 다시 부르면서 지워지던 것이다.

### 결정
1. **카드 목록 읽기는 그 덱의 별표 쓰기가 끝난 뒤에 출발한다.** `api/cards.ts`가 진행 중인 별표 쓰기를 덱별로 적어 두고(`trackStarWrite`), `fetchAllCards`는 그것이 모두 끝날 때까지(성공이든 실패든) 기다렸다가 읽는다. 일괄 설정(`setStarred`)과 카드 한 장 토글(`toggleCardStar`)이 스스로 적는다. 온라인 시험의 채점 저장은 켜기 → 끄기 **두 요청을 한 작업으로** 적는다 — 요청마다 따로 적으면 두 요청 사이의 빈틈에 목록 읽기가 끼어든다. 기다림은 길어도 8초다(응답이 영영 안 오는 요청 하나 때문에 목록이 안 뜨면 안 된다).
   - 이것 하나로 덮는 길: 저장 중에 '← 덱으로'·위쪽 메뉴로 나간 덱 화면, 덱 화면의 별표 요청과 목록 다시 읽기(문제 2의 뒤쪽), 듣기 채점 뒤 덱 화면, 낱말카드에서 ★를 누르자마자 나간 덱 화면.
2. **결과 화면의 '⭐ 별표만 복습 하러 가기'는 별표 저장 중·실패한 채로는 눌리지 않는다.** 옆의 '틀린 N개만 다시 풀기'와 같은 조건, 같은 안내 문구다. 저장이 실패한 채로 복습에 가면 틀린 단어가 빠진 목록이 되므로 결정 1만으로는 부족하다(실패한 쓰기도 '끝난' 것이라 기다림이 풀린다).
3. **덱 화면의 별표는 응답이 오기 전에 또 누르면 보내지 않는다.** 기다리는 표시는 두지 않고(누를 때마다 단추가 깜빡인다) 누름만 버린다. 느린 망에서 "안 눌렸나?" 하고 한 번 더 누른 경우에도 결과는 한 번 누른 것이다. 성공하면 앞서 뜬 오류 문구를 지운다.
4. **끈 별표 수 (ADR-063 결정 4 보완): 끄기 요청이 실패한 적 있는 카드는, 다시 시도가 성공했을 때 응답에 "바뀐 것 없음"으로 와도 센다** — 끄기 전에 ⭐였던 것으로 아는 카드(시험을 열 때 ⭐였거나 이 시험이 켠 것이 확인된 카드)에 한해서다. 실패가 없었으면 지금처럼 서버가 바꿨다고 답한 카드만 센다.

### 고려한 대안
- **복습 링크만 막기** — Codex가 짚은 길은 닫히지만 '← 덱으로'로 나간 덱 화면에는 같은 문제가 남는다(고치기 전 코드로 재현: 덱 화면이 저장 전 별표를 보여 줌). 화면마다 막는 것보다 읽는 쪽이 기다리게 하는 편이 빠짐이 없다.
- **복습·덱 화면이 저장이 끝난 뒤 목록을 다시 읽기** — 화면마다 '언제 다시 읽을지'를 알아야 하고, 보고 있는 목록이 바뀐다(낱말카드는 진행 중인 순서가 흔들린다 — 그래서 별을 떼도 다음 세션부터 반영하게 돼 있다).
- **별표 토글을 값 지정 요청으로 바꾸기**(켜기/끄기를 명시) — 겹친 요청의 결과가 순서와 무관해지지만 서버 API와 낱말카드 화면까지 바꿔야 한다. 응답 전의 두 번째 누름을 버리는 것으로 같은 결과를 얻는다.
- **두 번째 누름을 줄 세워 차례로 보내기** — 결과가 '원래대로'가 되는데, 느린 망에서 한 번 더 누른 사람이 바란 것은 그게 아니다.
- **끈 별표를 '시험이 아는 것'만으로 세기**(서버 응답을 안 봄) — 실패가 없을 때도 다른 탭에서 바뀐 별표를 잘못 센다. 그래서 실패한 적 있는 카드에만 쓴다.

### 잃는 것
- 별표 저장 중에 덱 화면으로 나가면 목록이 저장이 끝날 때까지(보통 1초 안, 길어도 8초) '불러오는 중'으로 보인다.
- 끄기 요청이 실패했고 그 사이 다른 탭에서 그 카드의 ⭐를 껐다면, 해제 수에 그 카드가 한 장 더 들어간다(표시만).
- 채점 저장 중에 탭을 닫거나 새로 고치면 뒤따르는 끄기 요청이 안 나갈 수 있다 — 전부터 그랬고 그대로다(다시 채점하면 맞춰진다).
- 저장 중에 '← 덱으로'나 위쪽 메뉴로 나간 뒤에 저장이 실패하면 그 오류를 보여 줄 화면이 없다(시험 화면에 남아 있어야 '별표 다시 시도'가 보인다) — 이것도 그대로다. 나간 화면은 실패한 뒤의 실제 서버 값을 보여 준다.
- 저장이 8초를 넘기면 나간 화면은 기다리지 않고 읽는다 — 그때는 고치기 전과 같다.

### 테스트 (새 테스트 7개를 고치기 전 코드 `cdc5ba5`의 jar로 먼저 돌려 7개 모두 실패를 확인했다)
- E2E `online-test.spec.ts` 4개
  - 저장 중에는 '별표만 복습'이 막혀 있고, 끝난 뒤 복습에는 틀린 단어만 나온다 — 고치기 전: 저장 중에도 눌림(`enabled`).
  - 저장 중에 '← 덱으로' 나가도 덱 화면은 저장 뒤의 별표 — 고치기 전: 틀린 단어의 별이 꺼진 채(옛 별표)로 남음.
  - 끄기 응답이 끊겨도(서버엔 반영) 다시 시도 뒤 `1개 해제` — 고치기 전: `점수 1/1`만 나옴.
  - 501개를 끄다 마지막 묶음이 끊겨도 다시 시도 뒤 `501개 해제` — 고치기 전: `점수 501/501 · 맞힌 단어의 ⭐ 1개 해제`.
- E2E `deck.spec.ts` 3개
  - 같은 별표를 응답 전에 또 눌러도 화면 = 서버 — 고치기 전: 화면 켜짐·서버 꺼짐(요청 2개).
  - 별표를 누른 직후 카드를 추가해도 화면 = 서버 — 고치기 전: 서버 ⭐인데 화면은 `별표 0장`.
  - 실패 뒤 다시 눌러 성공하면 오류 문구가 사라진다 — 고치기 전: 문구가 남음.
- 통신 지연·응답 순서·실패는 Playwright의 `page.route`로 만들었다(붙잡아 둔 요청도 실제 서버가 처리한다).
- 고친 뒤 로컬(빈 MySQL·Redis에 띄운 jar, Chromium): 전체 85개 통과 — 새 7개 포함, 실패 0. 서버 `AnswerMatcherTest` 99개, 화면 단위 검사 전부(`answerMatch` 112개, 번역 열쇠 615개), lint·tsc 통과. 서버 전체 테스트는 CI에서 돈다.

## ADR-065: 첫 화면 측정(Lighthouse)이 찾은 다섯 가지 — 글꼴 CSS 비차단·글자 대비·본문 표시·페이지 설명·nginx 버전 숨김

**상태:** 채택 (2026-11-25 → PR #46)
**범위:** `frontend/index.html`, `frontend/src/index.css`(`--muted`), `frontend/src/pages/*.tsx` 17개(본문 칸 `div` → `main`), E2E `a11y.spec.ts`, 운영 서버 `/etc/nginx/nginx.conf` 한 줄(CI 배포 밖 — `deployment-aws.md`)

### 문제
2026-11-25 chrome-devtools MCP로 운영 첫 화면 `/app/login`을 쟀다(데스크톱, 스로틀 없음, 1회). LCP 357 ms·CLS 0.00으로 빨랐고, Lighthouse는 접근성 93·권장사항 100·SEO 90(이 도구의 Lighthouse는 성능 점수를 빼고, 성능은 트레이스로 잰다). 걸린 것은 다섯 가지다.
1. **구글 글꼴 CSS가 첫 그리기를 막는다.** 우리 CSS는 82 ms에 끝났는데 글꼴 CSS(`fonts.googleapis.com/css2`)가 285 ms에 끝날 때까지 기다렸다(도구 추정 절감 327 ms). 모듈 스크립트도 앞선 스타일시트를 기다리므로 화면을 그리는 JS 실행까지 늦어진다.
2. **글자 대비** — 「구글로 로그인」·「계정이 없어요 → 회원가입」(`.auth-card .ghost`)의 글자색 `--muted` `#8a8b9c`가 흰 배경에서 3.35:1(기준 4.5:1). 같은 변수를 CSS 24곳이 쓴다.
3. **본문 표시(`<main>`)가 없다** — 로그인 화면만이 아니라 앱 전체에 한 군데도 없었다.
4. **페이지 설명(`<meta name="description">`)이 없다.**
5. 응답 머리글 `server: nginx/1.24.0 (Ubuntu)`가 버전을 드러낸다(같은 측정의 응답 머리글에서 보임).

### 결정
1. 글꼴 CSS는 `<link rel="preload" as="style" onload="this.rel='stylesheet'">`로 받는다 — HTML을 읽자마자 받기 시작하되 그리기를 막지 않고, 다 받으면 적용한다. 글꼴 파일은 원래 `display=swap`이라 받는 동안 대체 글꼴로 보이는 것은 전과 같다. 앱은 JS 없이 돌지 않으므로 `<noscript>` 대체는 두지 않는다.
2. **버튼 두 개가 아니라 변수를 고친다**: `--muted` → `#68697a`. 앱의 밝은 배경 전부에서 4.5:1을 넘는다(#fff 5.40 · 페이지 #fafafb 5.17 · 검색창 #f3f3f7 4.88 · 목록 #f1f1f6 4.79 · 메뉴 활성 #ececf7 4.60). `--sub #4e4f60`보다는 여전히 연하다.
3. 페이지마다 본문 칸(`.shell`, 로그인은 `.auth-page`)을 `div` → `main`으로(18곳 — 홈은 두 갈래). 클래스는 그대로라 CSS·E2E 선택자는 안 바뀐다. 창(modal)은 지금처럼 `.shell` 밖이다.
4. `index.html`에 한국어 설명 한 줄(로그인 화면 소개 문구와 같은 내용).
5. nginx `server_tokens off;` — `nginx.conf` http 블록에 있던 우분투 기본 주석을 푼다(백업 → `nginx -t` → reload). 버전을 숨긴다고 안전해지지는 않지만, 알려진 취약 버전을 찾는 자동 스캔에 주는 정보가 준다.

### 고려한 대안
- **글꼴을 우리 서버에서 내려주기** — 렌더 차단과 외부 요청이 함께 없어지지만 한글 글꼴은 커서 글자 범위별로 나눠 내려야 한다. 지금 문제(렌더 차단)는 preload로 풀린다.
- **JS에서 글꼴 CSS를 붙이기**(`i18n/index.ts`가 일·중 글꼴을 붙이는 방식) — CSP에는 안전하지만 JS 묶음을 받아 실행한 뒤에야 글꼴 CSS를 받기 시작한다. 운영에 CSP가 없으므로 더 일찍 받는 preload를 택한다.
- **두 버튼의 색만 바꾸기** — 같은 3.4:1 회색이 나머지 23곳(덱·통계 화면 라벨 등)에 남는다.

### 잃는 것·남긴 것
- HTML 안의 `onload` — 나중에 CSP를 켜면 이 줄을 허용(해시 등)해 줘야 한다.
- 회색 글자가 앱 전체에서 조금 진해진다.
- `--muted-2`(#9b9cab, 흰 배경 2.71:1 — 날짜·덱 카드 장수·학습 안내 등)는 이번 측정 범위(로그인 화면) 밖이라 그대로 둔다. 기준을 맞추면 `--muted`와 거의 같은 색이 되므로, 위계를 글씨 크기·굵기로 줄지부터 정할 일이다.
- 모바일·느린 망 조건, 로그인 뒤 화면의 Lighthouse는 이번에 재지 않았다.

### 테스트
- E2E `a11y.spec.ts` 4개: ① 로그인 화면 main 1개 + 설명 meta ② 두 버튼 대비 4.5:1 이상(계산 = WCAG 2 공식, 카드 배경 기준) ③ 서버가 주는 HTML에서 글꼴 CSS는 preload 하나뿐이고, 받으면 stylesheet로 바뀐다(글꼴 CSS는 가짜로 바로 줘서 CI 망에 기대지 않음) ④ 로그인한 화면 6개(홈·덱·탐색·통계·랭킹·설정)에 main 1개씩.
- 고치기 전 상태의 재현은 11/25 운영 측정(Lighthouse 실패 3개·렌더 차단 insight)이다. 로컬 Docker가 꺼져 있어 새 E2E를 고치기 전 jar로 돌리지는 않았다 — 새 E2E는 CI에서 처음 돈다.
