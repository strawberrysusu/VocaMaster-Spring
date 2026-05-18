# Week 2 (2026-05-12 ~ 2026-05-18)

## 한 일

- **Phase 1 완료** — refresh token rotation / reuse detection / httpOnly cookie /
  CustomUserDetails / `/users/me` API / 회원 탈퇴(soft delete)
- **Phase 2 진입 (~35%)** — Card 검색(keyword) / 정렬(sort) / 필드 확장(exampleSentence, memo, position)
- **ADR 19개 작성** (`docs/decisions.md`) — 모든 핵심 결정의 "왜 + 대안 + 트레이드오프" 기록
- **운영 룰 정비** — 작업 전 6단계 점검 루틴 + 작업 후 확인 루틴 + 학습 단위 통과 의식 (CHECKLIST)
- **예외 표준화 청소** — `ResponseStatusException` 7건 → 커스텀 예외로 교체 (046e625)
- **테스트 코드 학습 — `AuthServiceTest` 1~10번 완주** (← 이번 주 핵심 학습)

---

## 이해한 것 (스스로 설명할 수 있는 것)

### 1. 테스트 클래스의 뼈대

| 어노테이션 | 의미 |
|---|---|
| `@SpringBootTest` | 스프링 *전체 컨텍스트*를 띄움 — 진짜 빈/Repository/DB 동작 = 통합 테스트 |
| `@ActiveProfiles("test")` | `application-test.yml` 활성화 → H2 인메모리 DB |
| `@Transactional` (테스트) | 각 테스트 끝나면 *자동 롤백* → 테스트 간 데이터 격리 |

> 테스트의 `@Transactional`은 운영의 그것과 *목적이 다름*. 운영 = "같이 성공/실패", 테스트 = "끝나면 롤백해서 DB 깨끗하게".

### 2. Given / When / Then

- 메서드 *선언부*가 아니라 **본문(중괄호 안)을 의미별로 3토막** 낸 것
- **Given** = 사전 준비 (테스트 상황을 *만드는* 단계)
- **When** = 검증하려는 *바로 그 행동* — 보통 *한 줄*
- **Then** = 결과가 기대대로인지 `assert`

### 3. assertThrows — "예외가 나야 통과하는 감시원"

```java
assertThrows(BadRequestException.class, () -> action());
             ────────────────────────    ──────────────
             [Then] 이 예외 나야 함        [When] 이 행동 실행
```

- `assertThrows`에서는 **예외가 던져지는 게 정상(통과)**
- 3가지 경우:
  - 기대한 예외 던져짐 → ✅ 통과
  - 아무 예외도 안 던져짐 → ❌ 실패 ("나야 하는데 안 났어")
  - 다른 예외 던져짐 → ❌ 실패 ("기대한 거랑 다른 게 왔어")
- **assertThrows 테스트는 "나머지 Then"이 없다** — When+Then이 그 한 줄에 뭉쳐있음

### 4. 성공 테스트 vs 실패 테스트

| 검증하려는 것 | 쓰는 도구 |
|---|---|
| 예외를 *던지며 멈추는지* | `assertThrows` |
| *값을 리턴하고 그 값이 맞는지* | `assertNotNull` / `assertEquals` / `assertTrue` |

> "잘못된 결과값" ≠ "예외". assertThrows는 *예외 던짐*용.

### 5. AuthServiceTest 1~10번 — 각 한 줄 요약

| # | 테스트 | 검증 내용 |
|---|---|---|
| 1 | `register_success` | 회원가입하면 유효한 access·refresh 토큰 쌍이 나오고, type claim이 정확하며, user가 DB에 저장된다 |
| 2 | `register_duplicateEmail` | 이미 가입된 이메일로 또 가입하면 `BadRequestException`이 던져진다 |
| 3 | `login_success` | 가입된 user가 올바른 이메일·비번으로 로그인하면 유효한 토큰 쌍을 받고, user는 새로 생기지 않는다 |
| 4 | `login_wrongPassword` | 가입된 user가 *틀린 비번*으로 로그인하면 `UnauthorizedException`이 던져진다 |
| 5 | `login_emailNotFound` | 등록 안 된 이메일로 로그인하면 `UnauthorizedException` (4번과 *동일한* 예외) |
| 6 | `refresh_rotation_success` | refresh로 갱신하면 새 access·refresh 쌍이 나오고, 새 refresh는 옛것과 *다른 값* (rotation = 일회용). `assertNotEquals` 첫 등장 |
| 7 | `refresh_reuseDetection` | 폐기된 옛 refresh를 *재사용*하면 탈취 신호로 보고 그 사용자 *모든* refresh 폐기(mass logout). 옛 토큰도 정상 토큰도 둘 다 401 |
| 8 | `refresh_withAccessToken_rejected` | refresh 엔드포인트에 *access token*을 넣으면 거부. 토큰 안 `type` claim으로 구분 |
| 9 | `logout_revokesRefresh` | 로그아웃하면 그 refresh *하나만* 폐기(single logout). 7번 mass logout과 대비 |
| 10 | `deleteAccount_fullFlow` | 회원 탈퇴 통합 시나리오 — soft delete(deletedAt) + mass logout + 재로그인 차단. Then 3개 |

### 6. 관련 개념 (1~5번)

- **account enumeration 방지** — "없는 이메일"과 "틀린 비번"을 *같은* `UnauthorizedException`으로 응답 →
  공격자가 어느 이메일이 가입돼 있는지 *알아낼 수 없게*
- **중복 방지 이중 방어** — ① 서비스의 중복 체크(`BadRequestException`) + ② DB `UNIQUE` 제약(`DataIntegrityViolationException`).
  테스트는 ①을 검증. ②가 데이터를 지켜도 ①이 깨지면 테스트는 여전히 실패 = 옳은 동작
- **forensics 칼럼 연결** — `login(loginReq, UA, IP)`의 `UA`/`IP`는 발급되는 refresh_token row의
  `user_agent` / `last_used_ip`에 저장됨 (V2 마이그레이션 때 만든 추적용 칼럼)

### 7. 핵심 개념 (6~10번)

- **Rotation (회전)** — refresh는 *일회용*. 한 번 쓰면 폐기 + 새 refresh 발급. 그래서 옛것 ≠ 새것.
  탈취당해도 옛 토큰은 곧 무효 → 피해 최소화. reuse detection의 *전제*.
- **`assertNotEquals`** — "두 값이 *달라야* 통과, 같으면 실패". assert 5종째 (NotNull / Equals / True / Throws / NotEquals).
- **Reuse Detection** — 폐기된 토큰이 다시 등장 = 탈취 의심 → mass logout.
  *어느 쪽이 진짜 사용자인지 모르니까* 둘 다 강제 재로그인 (안전 우선).
- **single logout vs mass logout**
  - single (`logout`) = 사용자가 *직접* 로그아웃 → 그 토큰 1개만 (다른 기기 세션 유지). *정상* 상황.
  - mass (`revokeAllByUserId`) = 그 사용자 *전부* 폐기. *공격 의심 / 비번 변경 / 회원 탈퇴*.
  - **무엇이 가르나 = 상황이 "정상이냐 / 공격 의심이냐".**
- **type claim 검증** — 토큰 안에 `type`(`access`/`refresh`)을 박아둠.
  refresh 엔드포인트는 `type=refresh`만 받음 → access를 갱신용으로 못 쓰게 막아야 *access 1시간 만료가 의미 있음*.
- **soft delete** — 회원 탈퇴 시 row를 *지우지 않고* `deletedAt`에 시각만 박음 ("탈퇴 도장").
  조회는 *여전히 됨* (`findById`로 찾아짐) — 사용만 막음.
- **책임 분리 (deleteAccount vs login)**
  - `deleteAccount` = *표시* — deletedAt 박기 + mass logout. 탈퇴 *순간 한 번* 실행.
  - `login`의 `isDeleted()` 체크 = *확인* — 로그인 시도 *매번* 검사해서 탈퇴자 차단.
  - 차단은 *미래의 로그인 시도 순간*에 일어나야 하므로 `login` 안에 있어야 함 (한 번 도는 `deleteAccount`가 못 함).

---

## 헷갈렸다가 잡은 것

### 1~5번
- Given/When/Then은 *메서드 선언*이 아니라 *본문 구조*다
- `assertThrows` 테스트는 별도 "Then 줄"이 없다 — When+Then이 한 줄
- 실패 테스트는 *실패 조건을 의도적으로 세팅*한다 (중복 이메일 / 틀린 비번을 일부러 넣음)
- "잘못된 결과값"과 "예외"는 다른 것 — assertThrows는 예외용
- DB `UNIQUE`는 *테스트를 돕는 게 아니라* 데이터를 지키는 별개 층

### 6~10번
- `initial` / `rotated`는 *둘 다 그 테스트 안에서* 만든 것 — "예전 거"가 아니라 1초 차이로 먼저/나중
- `assertNotEquals`는 *같으면 실패* — "다른 게 정상"이라 헷갈렸음
- soft delete = 삭제가 아니라 *탈퇴 도장* — `findById`로 *여전히 찾아짐* (row가 남아있으니까)
- 재로그인 차단은 `deleteAccount`가 아니라 `login`의 `isDeleted()` 체크가 한다 (책임 분리)

---

## 아직 헷갈리는 것 / 다음 주

- `@RequiredArgsConstructor`, `@Transactional`(운영), `BIT(1) vs BOOLEAN` — week-1에서 넘어온 미해결
- 다른 테스트 클래스 (`CardServiceTest` / `QuizServiceTest` / `StudyServiceTest`) 는 아직 학습 X

---

## 예상 면접 질문

### Q1. 통합 테스트와 단위 테스트를 어떻게 나눴나요?

- `AuthServiceTest`는 `@SpringBootTest` — 전체 컨텍스트 로딩, 진짜 DB(H2)까지 동작하는 *통합 테스트*
- mock 없이 실제 흐름 (Service → Repository → DB)을 검증

### Q2. 테스트에서 `@Transactional`을 왜 붙였나요?

- 각 테스트 메서드가 끝나면 자동 롤백 → 테스트 간 데이터 격리
- test A가 만든 user가 test B에 남지 않음

### Q3. "이메일 없음"과 "비밀번호 틀림"을 같은 예외로 처리한 이유는?

- account enumeration 방지 — 응답이 다르면 공격자가 가입된 이메일을 수집 가능
- 둘 다 `UnauthorizedException`으로 통일해 이메일 존재 여부를 노출하지 않음

### Q4. 중복 이메일 가입은 어떻게 막나요?

- 서비스 레벨 중복 체크(`BadRequestException`) + DB `UNIQUE` 제약, 이중 방어
- 테스트는 서비스 레벨 동작을 검증

### Q5. Refresh Token Rotation이 뭔가요?

- refresh는 *일회용* — 한 번 쓰면 폐기되고 새 refresh가 발급됨
- 탈취당해도 옛 토큰은 곧 무효화 → 피해 최소화
- 옛 refresh가 다시 등장하면 *탈취 신호*로 감지 가능 (reuse detection의 전제)

### Q6. single logout과 mass logout의 차이는?

- single (`logout`) = 사용자가 직접 로그아웃 → 그 토큰 1개만 폐기, 다른 기기 세션 유지
- mass = 공격 의심 / 비번 변경 / 회원 탈퇴 → 그 사용자의 모든 refresh 폐기
- 가르는 기준 = 상황이 "정상이냐 / 공격 의심이냐"

### Q7. 회원 탈퇴를 hard delete가 아니라 soft delete로 한 이유는?

- row를 보존하므로 복구 가능 + 통계 유지 + FK cascade 부담 X
- `deletedAt`에 시각만 박고, `login`의 `isDeleted()` 체크로 차단
- 책임 분리 — `deleteAccount`는 *표시*, `login`은 *확인* (로그인 시도마다 검사)

---

## 다음 이어서 (resume point)

> **`AuthServiceTest` 10개 완주.** Phase 1 인증의 모든 핵심 테스트를 *왜 그렇게 동작하는지* 설명 가능.
>
> 다음 선택지:
> - (학습) 다른 테스트 클래스 — `CardServiceTest` / `QuizServiceTest` / `StudyServiceTest` / `ExpiredRefreshTest`
> - (개발) Phase 2 #3 일괄 등록 강화로 복귀 — ADR 점검 후 진행
