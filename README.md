# VocaMaster

> 망각곡선 기반 반복 학습으로 단어를 장기 기억에 안착시키는 무료 단어장 학습 서비스

영어 · 일본어 단어를 직접 등록하거나 다른 사용자가 만든 공개 단어장을 가져와,
플래시카드 · 5지선다 퀴즈 · 타이핑 모드로 학습하고, 박스 단계별 복습 알고리즘으로 다시 만나게 합니다.

> **상태:** 🚧 개발 중 (Phase 0 / 8) · **시작:** 2026-05 · **목표 마감:** 2027-01
> 진행도 상세는 [`docs/CHECKLIST.md`](docs/CHECKLIST.md), 8개월 로드맵은 [`docs/ROADMAP.md`](docs/ROADMAP.md) 참고.

---

## 🎯 만드는 이유

기존 단어 학습 서비스(Quizlet 등)가 유료화되면서 학습자들이 진입장벽을 겪는 문제를 해결하려 합니다.
"내가 직접 만들어서 무료로 쓰고, 같은 처지의 학습자에게 공유한다"가 기획의 출발점입니다.

기술적으로는 다음을 깊게 학습/구현하는 것을 목표로 합니다.

- **반복 학습 알고리즘** — Leitner Box 기반 망각곡선 복습
- **퀴즈 검증 무결성** — 정답을 항상 서버가 판정 (프론트 조작 불가)
- **공개 단어장 공유** — 검색 · 복사 · 좋아요 · 인기 랭킹
- **캐싱 / 비동기 / 부하 테스트** — 운영 가능한 백엔드 형태

---

## 📍 현재 진행 상태

### ✅ 구현 완료 (MVP 베이스)

| 영역 | 기능 |
|---|---|
| 인증 | 회원가입 / 로그인 (JWT) |
| 단어장 | Deck CRUD + 소유권 검증 |
| 카드 | Card CRUD · 별표 · 페이지네이션 조회 |
| 일괄 등록 | 텍스트 파싱으로 여러 카드 한 번에 등록 (preview 제공) |
| 퀴즈 | 5지선다 자동 생성 · 서버 측 정답 검증 · 시도 이력 저장 |
| 학습 | 학습 세션 · 안다/모른다 기록 · 덱별 통계 |
| UI | Mustache 기반 데모 페이지 (검증 및 시연 용도) |
| 문서 | Swagger UI (`/api-docs`) |
| 테스트 | Auth · Card · Import · Quiz · Study 서비스 단위 테스트 |

### 🚧 진행 중 (Phase 0 — 부트스트랩)

운영 가능한 형태로 뼈대를 정비하는 단계입니다.

- 환경별 설정 분리 (`application-{dev,test,prod}.yml`)
- 비밀 정보 환경변수화 (`.env.example` 제공)
- Flyway 도입 + `ddl-auto: validate`
- 공통 예외 응답 (`ErrorResponse`) 통일
- `BaseTimeEntity` 도입

### 📋 예정

| Phase | 주제 |
|---|---|
| 1 | Refresh Token Rotation · Reuse Detection · 회원 관리 |
| 2 | Card 필드 확장 · 검색/정렬 · Typing 모드 · 오답노트 |
| 3 | **Leitner Box 반복 학습 알고리즘** · 연속 학습일 |
| 4 | 공개 단어장 검색 · 복사 · 좋아요 · 태그 |
| 5 | Redis (인기 랭킹 · Rate Limit · 캐시) |
| 6 | 비동기 이벤트 (Spring Event → Kafka 선택) |
| 7 | Docker · Nginx · HTTPS · GitHub Actions · k6 부하 테스트 |
| 8 | 마감 · 면접 준비 |

---

## 🛠 기술 스택

**Backend** Java 17 · Spring Boot 3.3 · Spring Security · Spring Data JPA · Validation
**Auth** JWT (jjwt 0.12.5)
**Database** MySQL 8 (운영) · H2 (테스트)
**View** Mustache (데모 UI)
**Docs** springdoc-openapi (Swagger UI)
**Build/Test** Gradle · JUnit 5 · Spring Security Test

> Redis · Docker · CI/CD · 부하 테스트 등은 Phase 5 이후 단계적으로 도입 예정입니다.

---

## ▶️ 실행 방법

### 사전 준비

- JDK 17
- MySQL 8 (로컬에 `vocamaster` 데이터베이스 생성)

### 1. 환경변수 설정

`.env.example`을 참고하여 `.env` 파일을 만들거나, 다음 환경변수를 직접 설정합니다.

```bash
DB_URL=jdbc:mysql://localhost:3306/vocamaster?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=root
DB_PASSWORD=your-password
JWT_SECRET=your-secret-key-minimum-32-characters-long
```

> ⚠️ Phase 0 작업이 끝나기 전에는 `application.yml`의 기본값으로 동작합니다. 환경변수 분리 후 이 안내가 반영됩니다.

### 2. 실행

```bash
# Windows
gradlew.bat bootRun

# macOS / Linux
./gradlew bootRun
```

### 3. 접속

| 경로 | 설명 |
|---|---|
| `http://localhost:8080` | Mustache 데모 페이지 |
| `http://localhost:8080/api-docs` | Swagger UI (API 명세) |

### 테스트 실행

```bash
gradlew.bat test
```

---

## 📂 프로젝트 구조

```
src/main/java/com/vocamaster
├── auth/         # 인증 (회원가입/로그인/JWT)
├── user/         # User 엔티티
├── deck/         # 단어장 도메인
├── card/         # 카드 도메인
├── cardimport/   # 텍스트 일괄 등록
├── quiz/         # 5지선다 퀴즈
├── study/        # 학습 세션 / 기록 / 통계
├── page/         # Mustache 페이지 컨트롤러
├── common/       # 공통 (CurrentUser, GlobalExceptionHandler 등)
└── config/       # Security / Swagger / Jackson
```

---

## 📚 문서

| 문서 | 내용 |
|---|---|
| [`docs/CHECKLIST.md`](docs/CHECKLIST.md) | Phase 0 ~ 8 상세 체크리스트 (현재 진행도) |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | 8개월 로드맵 + 단계별 의도 |
| [`docs/ERD.md`](docs/ERD.md) | 데이터베이스 ERD |
| [`docs/notes/`](docs/notes/) | 주차별 학습 노트 |

> 위 문서들은 Phase 0 작업과 함께 채워집니다.

---

## 📝 라이선스

학습/포트폴리오 목적의 개인 프로젝트입니다.
