# Week 2 (2026-05-12 ~ 2026-05-18)

## 한 일

- **Phase 1 완료** — refresh token rotation / reuse detection / httpOnly cookie /
  CustomUserDetails / `/users/me` API / 회원 탈퇴(soft delete)
- **Phase 2 진입 (~35%)** — Card 검색(keyword) / 정렬(sort) / 필드 확장(exampleSentence, memo, position)
- **ADR 19개 작성** (`docs/decisions.md`) — 모든 핵심 결정의 "왜 + 대안 + 트레이드오프" 기록
- **운영 룰 정비** — 작업 전 6단계 점검 루틴 + 작업 후 확인 루틴 (CHECKLIST)
- **예외 표준화 청소** — `ResponseStatusException` 7건 → 커스텀 예외로 교체 (046e625)
- **테스트 코드 학습 시작** — `AuthServiceTest` 1~5번 이해 (← 이번 주 핵심 학습)

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

### 5. AuthServiceTest 1~5번 — 각 한 줄 요약

| # | 테스트 | 검증 내용 |
|---|---|---|
| 1 | `register_success` | 회원가입하면 유효한 access·refresh 토큰 쌍이 나오고, type claim이 정확하며, user가 DB에 저장된다 |
| 2 | `register_duplicateEmail` | 이미 가입된 이메일로 또 가입하면 `BadRequestException`이 던져진다 |
| 3 | `login_success` | 가입된 user가 올바른 이메일·비번으로 로그인하면 유효한 토큰 쌍을 받고, user는 새로 생기지 않는다 |
| 4 | `login_wrongPassword` | 가입된 user가 *틀린 비번*으로 로그인하면 `UnauthorizedException`이 던져진다 |
| 5 | `login_emailNotFound` | 등록 안 된 이메일로 로그인하면 `UnauthorizedException` (4번과 *동일한* 예외) |

### 6. 관련 개념

- **account enumeration 방지** — "없는 이메일"과 "틀린 비번"을 *같은* `UnauthorizedException`으로 응답 →
  공격자가 어느 이메일이 가입돼 있는지 *알아낼 수 없게*
- **중복 방지 이중 방어** — ① 서비스의 중복 체크(`BadRequestException`) + ② DB `UNIQUE` 제약(`DataIntegrityViolationException`).
  테스트는 ①을 검증. ②가 데이터를 지켜도 ①이 깨지면 테스트는 여전히 실패 = 옳은 동작
- **forensics 칼럼 연결** — `login(loginReq, UA, IP)`의 `UA`/`IP`는 발급되는 refresh_token row의
  `user_agent` / `last_used_ip`에 저장됨 (V2 마이그레이션 때 만든 추적용 칼럼)

---

## 헷갈렸다가 잡은 것

- Given/When/Then은 *메서드 선언*이 아니라 *본문 구조*다
- `assertThrows` 테스트는 별도 "Then 줄"이 없다 — When+Then이 한 줄
- 실패 테스트는 *실패 조건을 의도적으로 세팅*한다 (중복 이메일 / 틀린 비번을 일부러 넣음)
- "잘못된 결과값"과 "예외"는 다른 것 — assertThrows는 예외용
- DB `UNIQUE`는 *테스트를 돕는 게 아니라* 데이터를 지키는 별개 층

---

## 아직 헷갈리는 것 / 다음 주

- `AuthServiceTest` **6~10번 미완** — refresh rotation / reuse detection / logout / 회원 탈퇴
- `@RequiredArgsConstructor`, `@Transactional`(운영), `BIT(1) vs BOOLEAN` — week-1에서 넘어온 미해결

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

---

## 다음 이어서 (resume point)

> **`AuthServiceTest` 6번 `refresh_rotation_success` 부터.**
> 6~10번이 Phase 1의 핵심 — refresh rotation / reuse detection / logout / 회원 탈퇴 검증.
