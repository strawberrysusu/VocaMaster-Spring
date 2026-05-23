# 학습 모드 비교 (Flashcard / Quiz / Typing)

> VocaMaster의 3가지 학습 모드 한눈에. 면접/온보딩 답변용.

## 개요

| 모드 | 사용자 액션 | 채점/판정 | 비고 |
|---|---|---|---|
| **Flashcard** | 카드 보고 *안다/모른다* 클릭 | *주관적 자기 판정* | 가장 가벼움, 빠른 복습용 |
| **Quiz** | 4지선다 클릭 | *서버가 정답 판정* (정답 인덱스) | 객관식, 치트 방지 ↑ |
| **Typing** | 정답 *직접 입력* | *서버가 채점* (trim + ignoreCase + 쉼표 복수 정답) | 정확 암기 검증, 가장 엄격 |

## 코드/엔티티 매핑

| 모드 | 서비스 | 세션 엔티티 | 문제/기록 엔티티 | 컨트롤러 |
|---|---|---|---|---|
| Flashcard | `StudyService` | `StudySession` | `StudyRecord` | `StudyController` |
| Quiz (단발) | `QuizService.generateQuiz/submitAnswer` | — | `QuizAttempt` | `QuizController` |
| Quiz (세션) | `QuizService.startSession/submitAnswerToSession/getSummary` | `QuizSession` | `QuizQuestion` | `QuizSessionController` |
| Typing | `TypingService` | `TypingSession` | `TypingQuestion` | `TypingSessionController` |

## API 경로

| 모드 | 시작 | 답 제출 | 요약 |
|---|---|---|---|
| Flashcard | `POST /decks/{deckId}/study/sessions` | `POST /study/sessions/{id}/records` | `GET /study/sessions/{id}/summary` |
| Quiz 세션 | `POST /decks/{deckId}/quiz-sessions` | `POST /decks/{deckId}/quiz-sessions/{id}/answers` | `GET /decks/{deckId}/quiz-sessions/{id}/summary` |
| Typing | `POST /decks/{deckId}/typing-sessions` | `POST /decks/{deckId}/typing-sessions/{id}/answers` | `GET /decks/{deckId}/typing-sessions/{id}/summary` |

## 채점/판정 비교

| 모드 | 판정 주체 | 정규화 | 치트 가능성 |
|---|---|---|---|
| Flashcard | **사용자 본인** (`known=true/false` 직접 보냄) | 없음 (주관적) | 높음 (단 *자기 학습 목적*이라 무관) |
| Quiz | **서버** (선택지 인덱스로 판정) | 없음 | 낮음 (선택지만 클릭) |
| Typing | **서버** (`trim + equalsIgnoreCase + 쉼표 split`) | trim + ignoreCase + 쉼표 분리 | 매우 낮음 (직접 타이핑 + 정답 NULL 마스킹) |

## 세션 구조 비교

| 모드 | 세션 모델 | 문제 미리 생성? |
|---|---|---|
| Flashcard | *덱 통째* 카드 전부 로딩 → 사용자가 순회 | ❌ (단순 list) |
| Quiz (단발) | 세션 X — 호출마다 즉석 1문제 | ❌ |
| Quiz (세션) | **Eager** — 시작 시 N문제 row 미리 저장 | ✅ (ADR-024) |
| Typing | **Eager** — 시작 시 N문제 row 미리 저장 | ✅ (ADR-026, Quiz 패턴 재사용) |

## 관련 ADR

- **ADR-018:** Card 단위 vs ContentItem (콘텐츠 다양화)
- **ADR-021:** 테스트 정책 (boundary only)
- **ADR-024:** Quiz 세션 — Eager 생성
- **ADR-025:** 테스트 인프라 (Testcontainers + MySQL)
- **ADR-026:** Typing 모드 — Quiz Eager 재사용 + 채점 정책
- **ADR-027:** Flashcard 명확화 — 리네임 대신 javadoc + 문서 (이 문서)

## 향후 확장

- **Phase 3:** Leitner Box (간격 반복) — 어느 모드에든 *복습 우선순위*를 줄 수 있는 별도 layer
- **모드 4개+ 추가 시:** 공통 추상 (`AbstractSessionService`) 추출 검토 (ADR-027 결정 C로 전환 트리거)
- **TTS:** 음성 재생 (ADR-017) — Flashcard/Typing에 자연 결합
