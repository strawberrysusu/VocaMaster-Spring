# Week 1 (2026-05-04 ~ 2026-05-07)

## 한 일

- Phase 0 부트스트랩 완료 (커밋 4개)
  - README + CHECKLIST 작성
  - `application.yml`을 dev/test/prod로 분리, 비밀 정보 환경변수화
  - Flyway 도입 + V1 init schema 직접 작성
  - 4개 커스텀 예외 + ErrorResponse + GlobalExceptionHandler 통일

- Phase 1 시작 (커밋 3개)
  - `docs/auth-design.md` 작성 — refresh token rotation + reuse detection 설계
  - `V2__add_refresh_tokens.sql` — token_hash CHAR(64), forensics 칼럼 포함
  - `RefreshToken` 엔티티 + Repository (atomic UPDATE 쿼리, mass logout 쿼리)

## 이해한 것 (스스로 설명할 수 있는 것)

### Phase 0
- **환경변수화** — git에 비밀번호 안 올리려고
- **Flyway** — 운영 DB가 코드 바뀐다고 멋대로 안 바뀌게, 변경 이력 남기려고
- **예외 클래스 분리** — GlobalExceptionHandler에서 타입으로 잡아 자동으로 다른 HTTP status 응답
- **인증 일관성** — 이메일 없음 / 비번 틀림 둘 다 401로 통일하는 건 account enumeration 방지

### Phase 1
- **Soft delete (`revoked_at`)** — row 삭제 X, 폐기 시각만 박음. 그래야 *"폐기된 토큰이 다시 들어왔는지"* 판별 가능 (reuse detection 전제)
- **Reuse detection** — 폐기된 refresh가 다시 사용됨 = 탈취 신호. 어느 쪽이 진짜 사용자인지 모르니까 mass logout (둘 다 강제 재로그인)
- **SHA-256 vs bcrypt** — 비밀번호는 저엔트로피라 일부러 느린 bcrypt. 토큰은 256비트 random이라 brute-force 자체가 불가능 → SHA-256으로 충분
- **Atomic UPDATE (CAS)** — `UPDATE ... WHERE token_hash=? AND revoked_at IS NULL`. 동시 요청 두 개 와도 DB가 한 번만 성공시킴. 영향받은 row 수로 race 결과 판별. 락 없이 race-safe
- **forensics 칼럼** — user_agent, last_used_ip. 사고 났을 때 *"어디서 발급됐고 어디서 마지막 썼지?"* 추적용
- **CHAR vs VARCHAR** — SHA-256은 항상 64자 hex (고정) → CHAR(64)가 효율 ↑
- **FK의 부수효과** — MySQL은 FK 만들면 그 칼럼에 자동으로 인덱스 만듦 → mass logout (`WHERE user_id=?`) 빠름
- **`@ManyToOne` + LAZY** — 우리 입장에서 user는 "한 명". LAZY = "user 정보는 실제로 쓸 때까지 안 가져옴". 거의 항상 LAZY가 정답
- **`@JsonIgnore`** — JSON 응답에 그 필드 포함 X. 비밀번호·user 무한 참조 차단
- **`Optional<T>`** — "null일 수 있다"를 타입으로 표시. NPE 방지
- **JPQL vs Native** — JPQL은 엔티티/필드 이름 (`RefreshToken`, `r.tokenHash`). Native는 테이블/칼럼 이름. 단순 쿼리는 JPQL
- **`@Column(length=N)`은 VARCHAR 전제** — CHAR 원하면 `columnDefinition = "char(64)"` 명시. ddl-auto: validate가 부팅 시 잡아냄

## 헷갈리는 것

- `@RequiredArgsConstructor` — `final` 붙은 필드를 인자로 받는 생성자를 자동 생성
- `@Transactional` — SQL이 여러 줄 들어갈 때 (여러 작업이 같이 성공/실패해야 할 때 붙임)
- `BIT(1)` vs `BOOLEAN` — 아직 정확히 모름. 다음 주에 다시 정리
- `@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})` — LAZY 프록시 직렬화 오류 막는 마법인 건 알겠는데 *왜* 그 두 필드인지 메커니즘 모름. 외워두기만
- `@Modifying` 없이 `@Query`로 UPDATE 쓰면 어떻게 됨? 에러? 안 돌아감? 다음에 일부러 빼보고 확인

## 예상 면접 질문

### Q1. 왜 application.yml을 dev/test/prod로 분리했나요?

- 환경별 설정값이 다름 (DB, 보안 키 등)
- prod는 비밀 정보를 환경변수로 빼서 git/코드에 노출 X
- prod만 `ddl-auto: validate`로 두면 코드와 스키마 불일치 시 부팅 실패 → 운영 사고 방지

### Q2. Flyway 안 쓰면 안 되나요? `ddl-auto: update`로 충분하지 않나요?

- 변경 이력 추적 (V1, V2... 버전별 SQL 파일)
- 운영 DB가 코드 변경 따라 제멋대로 안 바뀜
- 환경 간 스키마 일관성 보장

### Q3. 예외 클래스를 4개나 만든 이유는?

- 타입별 자동 분류 (`NotFoundException` → 404, `ForbiddenException` → 403 등)
- GlobalExceptionHandler에서 HTTP status 자동 매핑
- 메시지 텍스트 파싱 안 해도 됨

### Q4. refresh token을 왜 SHA-256 해시로 저장? raw 저장이나 bcrypt는?

- raw 저장: DB 유출 시 토큰 즉시 사용 가능 → 위험
- bcrypt: 비밀번호용. 일부러 느려서 검증마다 수십~수백ms 부담. refresh는 매 호출 검증인데 적합하지 않음
- SHA-256: refresh token은 256비트 random이라 brute-force 자체가 불가능. 빠른 단방향 해시로 충분

### Q5. Rotation 시 동시 요청 처리?

- 같은 refresh로 2번 동시 호출 시나리오 (탭 2개, 네트워크 재시도)
- `UPDATE ... WHERE token_hash=? AND revoked_at IS NULL` 한 줄로 race 차단
- DB가 UPDATE를 한 번만 성공시킴. affected rows = 1 → 회전 성공, = 0 → 누가 이미 처리
- PESSIMISTIC_WRITE 같은 락 없이 안전

### Q6. Reuse detection이 뭐고 어떻게 구현?

- *폐기된 토큰이 다시 사용됨* = 탈취 신호
- atomic UPDATE에서 affected = 0이면 추가 SELECT
- row 있고 `revoked_at NOT NULL` → reuse → 그 사용자의 모든 refresh 폐기 (mass logout)
- 응답은 그냥 401 (공격자에게 단서 안 줌), 로그는 WARN
