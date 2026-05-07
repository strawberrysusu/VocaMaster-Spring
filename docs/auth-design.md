# 인증 설계 — Phase 1

> 목표: 신입 백엔드 면접에서 통할 인증 구조.
> 핵심: Refresh Token Rotation + Reuse Detection.
> 모드: 🔵 B (한 줄씩 이해하면서 구현)

---

## 1. 토큰 수명

| 토큰 | 수명 | 이유 |
|---|---|---|
| Access | **30분** | 탈취 시 노출 시간 짧게. 30분이면 사용자 작업 흐름 끊지 않음 |
| Refresh | **14일** | 매번 로그인 부담 ↓. 14일이면 rotation·reuse detection 의미 살아있음 |

> **면접 답변**: *"30분은 access token이 탈취돼도 노출 시간이 짧고, 14일은 사용자가 매번 로그인하지 않아도 되는 균형점. 더 길면 rotation의 reuse detection 의미가 약해짐."*

---

## 2. 저장 위치 + Cookie 속성

### Refresh = httpOnly Cookie

| 후보 | 채택 여부 | 이유 |
|---|---|---|
| `localStorage` / `sessionStorage` | ❌ | XSS 한 방에 토큰 통째 유출 |
| 메모리 (JS 변수) | △ | 새로고침 시 사라짐. SPA 전용 |
| **httpOnly Cookie** | ✅ | JS 접근 차단. CSRF는 SameSite로 방어 |

### Access = Body로 응답 → 클라이언트 메모리

수명이 짧아서 메모리 저장으로 충분. Cookie로 보내면 CSRF 신경 써야 해서 더 복잡.

### Cookie 속성

| 속성 | 값 | 이유 |
|---|---|---|
| `HttpOnly` | `true` | JS `document.cookie`로 못 읽음 (XSS 방어) |
| `Secure` | prod=`true` / dev=`false` | HTTPS에서만 전송 |
| `SameSite` | prod=`Strict` / dev=`Lax` | CSRF 방어. dev는 cross-origin 테스트 위해 Lax |
| `Path` | `/api/auth` | 인증 API에만 쿠키 붙음 |
| `Max-Age` | `1209600` (14일) | refresh 수명과 동일 |

> **면접 답변 (path 좁히는 이유)**: *"refresh token 쿠키가 다른 API 호출에도 자동으로 붙으면, 만약 그 API에 XSS·리다이렉트 취약점이 있을 때 토큰이 새어나갈 수 있음. 인증 엔드포인트에만 보내는 게 최소 노출 원칙."*

---

## 3. `refresh_tokens` 테이블 스키마

```sql
CREATE TABLE refresh_tokens (
    id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    token_hash    CHAR(64)     NOT NULL UNIQUE,    -- SHA-256 hex (64자)
    expires_at    DATETIME(6)  NOT NULL,
    revoked_at    DATETIME(6)  NULL,               -- soft delete
    created_at    DATETIME(6)  NOT NULL,
    user_agent    VARCHAR(255) NULL,               -- forensics: 발급 기기
    last_used_ip  VARCHAR(45)  NULL,               -- forensics: 마지막 사용 IP (IPv6 최대 45자)
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_refresh_user_id (user_id)            -- mass logout용
);
```

### 결정 이유

#### Hash = SHA-256 (bcrypt ❌)

| 항목 | Bcrypt | SHA-256 |
|---|---|---|
| 용도 | 비밀번호 (저-엔트로피) | 토큰 (고-엔트로피, 256비트 random) |
| 속도 | 일부러 느림 (brute-force 방어) | 빠름 |

> **면접 답변**: *"비밀번호는 brute-force 막으려고 일부러 느린 bcrypt를 쓰지만, refresh token은 256비트 random이라 brute-force 자체가 불가능. SHA-256으로 충분."*

#### Revoke = soft delete (`revoked_at`)

> **면접 답변**: *"Reuse detection 때문. hard delete 하면 폐기된 토큰이 다시 들어왔는지 알 수 없음. row를 남겨야 'revoked_at NOT NULL'로 구별 가능."*

#### Token 형식 = JWT with `jti`

DB에 저장: `SHA-256(JWT 전체)` → `token_hash`

#### 인덱스

- `token_hash UNIQUE` — refresh 검증 시 매번 lookup
- `idx_refresh_user_id` — mass logout 시 `WHERE user_id = ?` 쿼리

---

## 4. Rotation 흐름

### 시퀀스 — `POST /api/auth/refresh`

```
1. Cookie에서 refresh JWT 추출
2. JWT 검증: 서명 / exp / type=refresh
3. SHA-256(JWT) → refresh_tokens 조회
4. 검증: revoked_at IS NULL  AND  expires_at > now()
5. ── 트랜잭션 시작 ──
6.   현재 row 폐기 (revoked_at = now())
7.   새 access + 새 refresh 발급
8.   새 refresh의 hash로 새 row INSERT (user_agent, last_used_ip 포함)
9. ── 트랜잭션 끝 ──
10. 응답: access는 body / 새 refresh는 cookie
```

### 트랜잭션 경계 = 6~8 한 묶음

> **이유**: 폐기는 성공했는데 새 토큰 INSERT가 실패하면 사용자가 *완전히 로그아웃됨*. 둘은 원자적이어야 함.

### 동시성 = Atomic UPDATE (CAS)

```sql
UPDATE refresh_tokens
   SET revoked_at = NOW(), last_used_ip = ?
 WHERE token_hash = ? AND revoked_at IS NULL
```

- affected rows = 1 → 회전 성공, 새 토큰 발급
- affected rows = 0 → 누가 이미 사용함 → reuse 판별 (다음 섹션)

> **면접 답변**: *"두 요청이 동시에 와도 DB가 한 번만 UPDATE 성공시킴. 락 안 잡고도 race-safe. PESSIMISTIC_WRITE보다 가볍고 OptimisticLock 예외 처리도 불필요."*

---

## 5. Reuse Detection — 차별 포인트

### 시나리오

```
1. 공격자가 victim의 refresh 토큰 탈취 (XSS / 네트워크 스니핑)
2. 둘 중 누가 먼저 refresh 호출하면 → 그 토큰 회전, 다른 쪽 보유 토큰은 폐기 상태
3. 폐기된 토큰을 가진 쪽이 다시 시도 = REUSE 발생
4. 서버는 어느 쪽이 진짜 사용자인지 모름 → 둘 다 로그아웃 (mass logout)
```

### 감지 + 대응 로직

Q4의 atomic UPDATE에서 affected rows = 0일 때, 이유 판별:

```sql
-- 추가 SELECT로 확인
SELECT revoked_at FROM refresh_tokens WHERE token_hash = ?
```

| 조건 | 의미 | 동작 |
|---|---|---|
| row 없음 | 잘못된 토큰 (위조 / 만료 후 정리됨) | 401 |
| row 있고 `revoked_at NOT NULL` | **REUSE DETECTED** | mass logout + WARN log + 401 |

### Mass Logout 쿼리

```sql
UPDATE refresh_tokens
   SET revoked_at = NOW()
 WHERE user_id = ? AND revoked_at IS NULL
```

→ 그 사용자의 *모든 활성 refresh 폐기*. `idx_refresh_user_id` 사용.

### 응답 + 로깅

| 항목 | 결정 |
|---|---|
| HTTP 응답 | **401** (구체 에러 코드 노출 X — 공격자에게 단서 안 줌) |
| 로그 레벨 | **WARN** (user_id, IP, user_agent, jti 기록) |
| 사용자 알림 (이메일 등) | 스코프 밖 |

> **면접 답변**: *"한 번의 atomic UPDATE로 race도 막고 reuse 감지까지 해결. 정상이면 affected=1, 비정상이면 0이라는 신호. 0일 때만 추가 SELECT로 폐기 토큰인지 확인."*

---

## 6. 비밀번호 변경 / 로그아웃

### 비밀번호 변경 (`PATCH /api/users/me/password`)

→ **Mass logout**. 비번 유출 의심 가능성. 다른 기기까지 전부 폐기.

```sql
UPDATE refresh_tokens
   SET revoked_at = NOW()
 WHERE user_id = ? AND revoked_at IS NULL
```

### 로그아웃 (`POST /api/auth/logout`)

→ **현재 토큰만 폐기**. 다른 기기 로그인은 유지.

```sql
UPDATE refresh_tokens
   SET revoked_at = NOW()
 WHERE token_hash = ? AND revoked_at IS NULL
```

### Cookie 처리

응답에 `Set-Cookie`로 같은 path / 빈 값 / `Max-Age=0` → 브라우저가 cookie 삭제.

### HTTP 메서드 = POST

> 왜 GET 아님? — CSRF 방어. 로그아웃은 *상태 변경 작업*이라 POST가 맞음.

---

## 7. JWT Claim 설계

### Access Token

```json
{
  "sub": "123",
  "type": "access",
  "iat": 1764547200,
  "exp": 1764549000
}
```

### Refresh Token

```json
{
  "sub": "123",
  "type": "refresh",
  "jti": "550e8400-e29b-41d4-a716-446655440000",
  "iat": 1764547200,
  "exp": 1765752000
}
```

### 결정 이유

| Claim | 포함 여부 | 이유 |
|---|---|---|
| `sub` | ✅ | user.id |
| `type` | ✅ | access vs refresh 혼용 방지 |
| `jti` | refresh에만 | 토큰 식별·로깅·forensics용 UUID |
| `iss` / `aud` | ❌ | 모놀리스 단일 서비스, 불필요 |
| `email` / `role` 등 | ❌ | stale 위험 (비번/권한 변경 후에도 옛 정보) |

> **면접 답변**: *"토큰은 minimum claims로 가고, 권한 같은 변동 가능 정보는 DB에서 매번 조회. stale 데이터 위험 차단. 모놀리스라 매 요청 DB 조회 부담도 적음."*

---

## 8. 회원 관리 API + CustomUserDetails

### API 4개

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/users/me` | 내 정보 |
| PATCH | `/api/users/me` | 닉네임 변경 |
| PATCH | `/api/users/me/password` | 비번 변경 → mass logout |
| DELETE | `/api/users/me` | 회원 탈퇴 (soft delete, `deleted_at`) |

### CustomUserDetails 도입

**왜 바꾸나?** — 현재 `CurrentUser.get()`은 SecurityContext에서 `User`로 직접 캐스팅. Spring Security 표준에서 벗어남 → 면접에서 약점.

```java
public class CustomUserDetails implements UserDetails {
    private final Long userId;
    private final String email;
    private final String passwordHash;
    // getAuthorities(), isEnabled() 등 표준 메서드
}
```

컨트롤러는 표준 패턴으로:

```java
@GetMapping("/me")
public ResponseEntity<UserResponse> me(
    @AuthenticationPrincipal CustomUserDetails me
) {
    return ResponseEntity.ok(userService.findById(me.getUserId()));
}
```

### 페이지네이션 안전장치

- `page < 0` → `0`으로 보정
- `size` → `1 ≤ size ≤ 100` 제한 (악의적 `size=1000000` 차단)

---

## 9. 테스트 케이스 목록

### `AuthServiceTest` (핵심)

1. Refresh rotation 성공 — 새 access·refresh 발급, 옛 row 폐기
2. 만료된 refresh 거부 — 401
3. **Reuse detection** — 폐기 토큰 재사용 시 mass logout
4. 동시 refresh 2개 호출 — 1개만 성공, 다른 1개 401
5. 비밀번호 변경 후 기존 refresh 사용 시 401
6. 로그아웃 후 그 refresh 401 (다른 기기 토큰은 살아있음 검증)

### `DeckServiceTest` (체크리스트 보강)

7. 남의 덱 접근 시 403

### `UserServiceTest`

8. 닉네임 변경
9. 비번 변경 — old password 검증
10. 회원 탈퇴 — `deleted_at` 세팅, 이후 로그인 401

---

## 10. 구현 순서

```
1. V2__add_refresh_tokens.sql 마이그레이션
2. RefreshToken 엔티티 + Repository
3. JwtProvider 확장 (access/refresh 발급, jti, type claim)
4. AuthService: 로그인 시 access + refresh 발급, refresh 저장
5. AuthController: POST /api/auth/refresh
6. AuthService: rotation + reuse detection 로직
7. AuthService: logout
8. CustomUserDetails 도입 + CurrentUser 제거
9. UserController + UserService (me, 닉네임, 비번, 탈퇴)
10. 테스트 1~10번 작성
11. 수동 테스트 (Postman / Swagger UI)
```

---

## 11. 면접 예상 질문 (10개)

1. 왜 access 30분 / refresh 14일?
2. refresh를 왜 httpOnly cookie에 두나? body로 주면 안 되나?
3. Cookie path를 `/api/auth`로 좁힌 이유?
4. SameSite=Strict / Lax 차이 + 언제 어느 거 쓰나?
5. refresh를 왜 SHA-256 해시로 저장? bcrypt는?
6. Rotation이 뭐고, 왜 필요한가?
7. **Reuse detection이 뭐고, 어떻게 구현했나?**
8. 동시에 같은 refresh로 2번 호출되면 어떻게 처리?
9. 비밀번호 변경하면 다른 기기 토큰은 어떻게?
10. JWT claim에 email/role을 왜 안 넣었나?

---

## 12. Out of Scope (Phase 1에서 안 함)

- ❌ Social login (OAuth)
- ❌ 2FA / TOTP
- ❌ 이메일 인증 (회원가입 후 자동 활성화)
- ❌ 비밀번호 재설정 (forgot password)
- ❌ Refresh token family 추적 (mass logout으로 충분)
- ❌ 만료 토큰 cleanup 스케줄러 (STRETCH로 추후)
- ❌ Rate limiting (Phase 5 Redis에서)
