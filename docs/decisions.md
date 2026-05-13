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

**상태:** 채택 (2026-05-12, A 시작은 Phase 5 React 도입 시 / C 캐싱은 Phase 5 Redis 작업과 결합)
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
