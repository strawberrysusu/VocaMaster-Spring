# Week 3 (2026-05-19 ~ 2026-05-24)

## 한 일

- **Phase 2 완전 종료 (#3 ~ #7 한 번에)**
  - #3 **일괄 등록 강화** — 1000줄 상한 / 실패 라인 분리 / 구분자 자동 감지 / 중복 카드 skip
  - #4 **Quiz 세션 단위 관리** — 시작 시 N문제 미리 생성(Eager), 자동 종료, 안 푼 정답 마스킹
  - #5 **Typing 모드** — 직접 입력 채점 (trim + 대소문자 무시 + 쉼표 복수 정답)
  - #6 **Flashcard 모드 명확화** — 기존 `StudyService`에 javadoc + `learning-modes.md`
  - #7 **통합 오답노트** — Quiz/Typing/Flashcard 3 모드 합쳐 한 API로 조회 (Aggregator)
- **테스트 인프라 전면 전환** — H2 → **Testcontainers + 진짜 MySQL 8**
  - 운영과 동일 DB 환경에서 통합 테스트
  - WSL Ubuntu + Docker unix socket으로 안정 셋업
- **ADR 8개 추가** — ADR-020 ~ ADR-028 (`docs/decisions.md`)
- **개발 협업 룰 전면 재정립**
  - **3 모드** (⚪보일러 / 🟢코어 / 🔴폐쇄훈련)
  - **3슬롯 요약** (선택 / 대안 / 그럼에도)
  - **🟢 코어 작업 흐름** — *설명 → 내가 재설명 → 통과 판정 → 박기* 순서
  - 코드 박을 때 📍위치 / 👀볼 부분 보고 의무화
- **테스트 50개 통과** (이전 41 + 새 9, 회귀 0)

---

## 이해한 것 (스스로 설명할 수 있는 것)

### 1. Eager vs Lazy — 미리 만드냐 vs 그때그때 만드냐

> Eager = "뷔페에서 시작 시 접시에 다 담아둠"
> Lazy  = "한 접시 다 먹고 다음 거 가지러 감"

| 패턴 | 정의 | 우리 적용 |
|---|---|---|
| **Eager** (Quiz/Typing 세션) | 시작 시 N문제 row를 *미리* DB에 저장 | `startSession`이 한 번에 `QuizQuestion` N개 INSERT |
| **Lazy** (JPA 지연로딩) | 엔티티 *접근할 때*까지 DB 조회 미룸 | `@ManyToOne(fetch = FetchType.LAZY)` |

→ **같은 영단어가 두 군데서 같은 정신으로 쓰임.** 둘 다 "*미루기 vs 미리하기*" 선택.

**왜 Eager (퀴즈에서)?**
1. **일관성** — 시작 후 카드 추가/삭제돼도 그 세션은 *고정*
2. **중복 출제 자동 방지** — 미리 N개 골라놨으니 같은 카드 두 번 나올 일 없음
3. **요약/통계 자연스러움** — DB에 다 있음 (`WHERE session_id=?`)

> 비유: 시험 시작 *전*에 문제지 인쇄 vs 문제 풀고 다음 거 즉석 인쇄. *시험지 추가/취소돼도* 내 문제지는 무사.

### 2. Anti-cheat — 안 푼 문제의 *정답* 은 NULL로 마스킹

```java
.correctAnswer(q.getAnsweredAt() != null ? q.getCorrectAnswer() : null)
```

- 사용자가 *중간에* `getSummary` 호출하면 → *남은 문제의 정답까지 노출* 위험
- **해법:** *푼 문제만* 정답 공개, *안 푼 문제*는 null
- 부수 효과: **Pause/Resume**(중단 후 이어서 풀기) 기능 자동으로 가능해짐

### 3. 채점 정책 (Typing) — 중간 길

| 슬롯 | 내용 |
|---|---|
| ① 선택 | `trim()` + `equalsIgnoreCase()` + **쉼표 split** |
| ② 대안 | 엄격(`equals`) / 관대(Levenshtein) / 정규화 강화 |
| ③ 그럼에도 중간 | 엄격 → 사용자 화남, 관대 → 학습 의미 사라짐. *중간 + 문서화* 정공 |

**쉼표 복수 정답** (Quizlet 패턴):
```
correct_answer = "사과, 능금"
사용자 "사과" → ✅ (둘 중 하나만 맞히면 정답)
사용자 "능금" → ✅
사용자 "apple" → ❌
```

**한계 (의도적):** 답에 쉼표 자체 포함 (`"Hello, World"`) → 깨짐. 단어 학습이라 드뭄.

### 4. Aggregator 패턴 — 흩어진 데이터 한 곳에 합치기

**오답이 3 모드에 흩어져 있음:**
- Quiz → `quiz_attempt.isCorrect = false`
- Typing → `typing_questions.isCorrect = false`
- Flashcard → `study_record.known = false`

**Aggregator(`WrongNoteService`)의 일:**
```
3 Repository 호출 → 카드 ID 수집 → 중복 제거(LinkedHashSet)
→ 카드 정보 한 번에 조회 → 모드별 + 통합 응답
```

**왜 별도 테이블 (`wrong_cards`) 안 만들었나?**
- 데이터가 *이미* 3 테이블에 있음 → 새 테이블 = *중복 저장* + *동기화 트리거*
- *premature optimization* 회피
- 학습 서비스 규모엔 *서버 합산*이 충분히 빠름

### 5. Testcontainers — 진짜 MySQL로 테스트

**왜 H2 폐기?**
- H2와 MySQL은 *JSON / 함수 / 락 / 인덱스* 동작 미묘하게 다름
- 우리 `quiz_questions.choices_json JSON` 컬럼이 *H2에서 깨짐* → 직접 당함
- **"테스트는 통과해도 운영에서 깨질 수 있다"** — 현장 안티패턴

**Testcontainers 정신:**
- Docker로 *진짜 MySQL 8 컨테이너* 띄움
- 모든 통합 테스트가 *같은 컨테이너 1회* 공유 (`@Container static` + reuse)
- Flyway까지 실제 실행 → 운영과 100% 동일

### 6. JPA "영속 컨텍스트" — 화이트보드 비유

> JPA는 DB와 *바로* 일 안 함. 사이에 **화이트보드**(영속 컨텍스트)를 둠.

| 단계 | 동작 |
|---|---|
| 조회 | DB → 화이트보드에 *복사본 메모* |
| 수정 | 화이트보드에서 *변경 추적* (dirty checking) |
| 트랜잭션 끝 | 화이트보드 → 진짜 DB로 옮김 = **flush** |
| 트랜잭션 끝 | 화이트보드 *지움* = **clear** |

**우리 코드 예:**
```java
question.setSelectedAnswer(...);  // 화이트보드에만 메모
// 별도 save() 호출 X
// → @Transactional 끝날 때 자동 flush
```

**테스트에서 `em.flush() + em.clear()` 왜 필요?**
- `repository.findById()`가 *화이트보드 캐시 먼저* 봄 → 캐시에 *수정한 객체*가 있으면 그대로 반환
- *DB에 진짜 갔는지* 검증 안 됨 → "테스트 통과인데 운영에선 깨짐" 위험
- → `flush` (화이트보드 → DB) + `clear` (화이트보드 지움) → 다음 조회는 *DB 진짜로 감*

### 7. Set vs for 루프 — 목적이 다름

| | Set | for 루프 |
|---|---|---|
| 목적 | *유일성 보장* (중복 제거) | *순회* (각 원소 작업) |
| 끝나는 시점 | 다 채워야 끝 | **조기 종료 가능** (`return`) |
| Quiz의 `Set<String> seen` | *선택지 축적*에 dedup | — |
| Typing의 `isAnswerMatch` | — | *매칭 발견 즉시 끝* |

### 8. NULL 안전 패턴 — `Boolean.TRUE.equals(x)`

| 코드 | x가 null일 때 |
|---|---|
| `x.equals(true)` | 💥 NullPointerException |
| `Boolean.TRUE.equals(x)` | ✅ `false` 반환 (안전) |

- `Boolean.TRUE`는 *상수*라 절대 null 아님 → `.equals(null)` 호출 안전
- `isCorrect`가 NULL일 수 있는 (아직 안 푼 문제) 상황의 *표준 패턴*

### 9. 자바 표현 정리 (Phase 2에서 자주 본 것)

| 표현 | 의미 | 한 줄 예 |
|---|---|---|
| `trim()` | 양 끝 공백 제거 | `"  사과  ".trim()` → `"사과"` |
| `equalsIgnoreCase()` | 대소문자 무시 비교 | `"Apple".equalsIgnoreCase("apple")` → `true` |
| `Math.min(a, b)` | 작은 값 선택 (fallback) | `Math.min(4, 3)` → `3` (5지선다 못 만들면 3지선다) |
| `stream().filter().count()` | 컨베이어 벨트에 올려 필터+개수 | for 루프 압축형 |
| `q -> q.getX()` | **람다** (한 줄 익명 함수) | "q 받아서 → q.getX() 반환" |
| `for (Type x : list)` | **for-each** — list에서 x 하나씩 꺼냄 | 콜론 `:` = "~에서" |
| `Set<X> seen` | 중복 차단 컬렉션 | dedup 필요할 때 |
| `LinkedHashSet` | 순서 보존 + dedup | "Quiz 본 사람한텐 Quiz 먼저" UX |
| `Optional<X>` | "null일 수 있다"를 타입으로 | `.orElseThrow(() -> ...)` |
| `@Transactional` | 한 묶음으로 성공/실패 | flush + clear 자동 |
| `BIT(1)` ↔ `Boolean` | 1비트 boolean (NULL OK) | "아직 안 됨" = NULL |

### 10. ADR (Architecture Decision Record)

- 결정마다 *컨텍스트 / 대안 3개+ / 결정 / 근거 / 트레이드오프* 기록
- "대안이 1개만 떠오르면 *고민 부족*" — 더 찾기
- **3슬롯 패턴** = ADR의 일상화: 박는 코드마다 *① 선택 / ② 대안 / ③ 그럼에도* 한 줄
- Week 3에 ADR-020 ~ ADR-028 9개 추가 (누적 28개)

---

## 헷갈리는 것

- **`@ServiceConnection`** — Spring Boot 3.1+의 *자동 datasource 주입* 어노테이션. 단 `spring-boot-testcontainers` 3.3.0이 Testcontainers 1.19.x 의존인데 우리는 1.21.3 강제 → *모듈 충돌*로 작동 X. **결국 옛 표준 `@DynamicPropertySource`로 회귀**. 두 패턴 차이 머리에 *완벽히 안 박힘* — 폐쇄훈련에서 다시.
- **`@DependsOn` / Bean 생성 순서** — Flyway가 dataSource보다 *먼저* 빌드 시도해서 깨졌던 경험. *Bean 의존 순서*를 명시적으로 통제하는 방법 자세히 모름. 다음에 일부러 만들어 볼 가치.
- **`@JdbcTypeCode(SqlTypes.JSON)`** — Hibernate 6 신문법. JSON 컬럼 ↔ Java `List<String>` 자동 매핑. *우리는 String + ObjectMapper로 우회*했지만 *언제가 더 적합한지* 모름.
- **Docker 29.4 + API 버전 핀 (`api.version=1.41`)** — 친구 도움으로 해결했지만 *왜 옛 API 강제로 해결되는지* 메커니즘 모름. *Docker REST API 버전 협상* 공부 거리.
- **Levenshtein 거리** — 이름만 들음. *오타 1~2개 허용* 알고리즘. STRETCH라 지금 미도입. 다음 Phase에서 도입 시 학습.

---

## 예상 면접 질문

### Q1. Quiz 세션을 왜 Eager로 설계했나요?

**짧은 답:** 시작 시 N문제를 미리 DB에 저장해서 *세션 일관성*을 보장하고 *중복 출제를 자동으로 방지*하기 위해.

**자세히:**
- *Lazy(즉석 출제)*는 사용자가 카드 추가/삭제하면 세션 도중 문제가 변할 수 있음
- *중복 방지*도 서비스 코드로 "이미 낸 카드" 추적해야 함 → 복잡
- Eager는 *DB row 미리 N개 저장* 비용 외엔 단점 없음 (학습 서비스 규모에선 무관)
- 면접 한 줄: "세션 시작 시 문제 확정 → *카드 변동 무관 일관성*"

### Q2. 안 푼 문제의 정답을 NULL로 마스킹한 이유?

- 사용자가 세션 *진행 중* 요약을 호출하면 *안 푼 문제의 정답까지 노출* 위험
- 해법: `correctAnswer = answeredAt != null ? real : null`
- 부수 효과: **Pause/Resume** 기능 자동 지원 (안 푼 문제는 정답 안 봤으니 나중에 풀어도 공정)

### Q3. Typing 모드 채점 정책 자세히

- **3단계 정규화:** `trim()` + `equalsIgnoreCase()` + 쉼표 split
- 너무 엄격(`equals`) → 사용자 화남
- 너무 관대(Levenshtein 오타 허용) → 학습 의미 사라짐
- 중간 길 + `docs/typing-policy.md`로 명시 문서화
- **한계:** 답 자체에 쉼표 포함 (`"Hello, World"`) → 깨짐. 단어 학습이라 드뭄, 의식적 트레이드오프

### Q4. Testcontainers를 왜 도입했나요? H2와 차이?

- H2와 MySQL은 *JSON / 함수 / 락 / 인덱스* 동작이 미묘하게 다름
- 우리 `quiz_questions.choices_json` 컬럼이 *H2에서 깨짐* → 직접 당한 사고
- **"테스트는 통과해도 운영에서 깨진다"** 안티패턴 회피
- Testcontainers = Docker로 *진짜 MySQL 8* 띄워서 *운영과 동일* 환경 검증
- 부수: Flyway 마이그레이션도 실제 실행 (운영과 100% 동일)
- 2024년 이후 사실상 표준

### Q5. Aggregator 패턴이 뭐고 왜 도입했나요?

- 흩어진 데이터를 *한 곳*에서 *합성된 뷰*로 제공하는 패턴
- 오답이 Quiz/Typing/Flashcard 3 테이블에 있는데 사용자는 *한 화면에서 보고 싶음*
- **3 대안 비교:**
  - (B) API 3개 따로 → 클라이언트가 합치기 → 사용성 ↓
  - (C) 새 `wrong_cards` 테이블 → 데이터 중복 + 동기화 트리거 → premature optimization
  - (A) ✅ Aggregator — 서버에서 3 Repository 호출 + dedup → 기존 자원 재활용

### Q6. JPA 영속 컨텍스트와 dirty checking?

- JPA는 DB와 사이에 *화이트보드*(영속 컨텍스트)를 둠
- 엔티티 수정 시 *별도 save() 호출 X* — 화이트보드에 변경만 메모
- 트랜잭션 끝 → 자동 flush (DB로 옮김) + clear (화이트보드 지움)
- **테스트에서 `em.flush() + em.clear()` 필요한 이유:** `findById`가 *화이트보드 캐시 먼저* 봐서 *DB 진짜 갔는지 검증 못 함*. 강제로 옮기고 지우면 다음 조회는 *DB 진짜로 감*

### Q7. 디버깅 사례 — 39개 테스트 실패의 진짜 원인?

- 증상: AuthServiceTest 10개 + 그 외 29개 = **39 fail**
- 처음엔 *@ServiceConnection 작동 안 함*, *Spring Boot vs Testcontainers 버전 충돌* 등으로 잘못 추적
- **진짜 원인:** AuthServiceTest 1개에 `extends AbstractIntegrationTest`가 **빠져 있었음** (Bulk Edit 누락)
- 그 1개가 컨텍스트 캐시를 *실패한 상태로 점령* → 모든 테스트가 *그 캐시 재사용*하며 줄줄이 fail
- **교훈:** *grep으로 일괄 검증* 한 번이면 5분에 잡힐 거였음. *시간 단축 = failFast + 짧은 진단 명령*

### Q8. Set vs for 루프 선택?

- Set = *유일성 보장* (dedup이 목적)
- for = *순회* (특히 *조기 종료* 가능)
- Quiz의 선택지 중복 방지 → Set (축적)
- Typing의 `isAnswerMatch` → for (매칭 발견 즉시 `return true`)

---

## 운영/디버깅에서 배운 것 (코드 외)

- **WSL Ubuntu + Docker unix socket이 Windows 명시 pipe보다 안정.** Docker 29.4 + named pipe 호환성 문제로 *Windows 직접*은 어려움. WSL Ubuntu에서 Gradle 돌리고 Docker는 WSL Integration으로.
- **friend/Stack Overflow/AI에 묻는 건 시니어도 함.** 현업 99%가 그래. 부끄러운 일 아님.
- **🟢 코어 작업 순서 = *설명 → 재설명 → 통과 → 박기*.** 박은 다음 통과 의식하면 *복붙 누적* 발생.
- **3슬롯 (① 선택 / ② 대안 / ③ 그럼에도)** = 면접 답변 *기본 템플릿*. 시니어/주니어 갈리는 지점.

---

## 다음 주(Week 4) 미리보기

- **Phase 3 Leitner Box 진입** — 간격 반복 알고리즘
- 우리 *오답노트(Phase 2 #7)*가 *복습 우선순위* 입력으로 자연 연결
- 면접 단골 ⭐⭐⭐ 영역
- 주말 폐쇄훈련 후보: `WrongNoteService.getWrongNotes` (90분, 인터넷 X)
