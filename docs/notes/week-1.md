# Week 1 (2026-05-04 ~ 2026-05-06)

## 한 일

- Phase 0 부트스트랩 완료 (커밋 4개)
  - README + CHECKLIST 작성
  - `application.yml`을 dev/test/prod로 분리, 비밀 정보 환경변수화
  - Flyway 도입 + V1 init schema 직접 작성
  - 4개 커스텀 예외 + ErrorResponse + GlobalExceptionHandler 통일

## 이해한 것 (스스로 설명할 수 있는 것)

- **환경변수화** — git에 비밀번호 안 올리려고
- **Flyway** — 운영 DB가 코드 바뀐다고 멋대로 안 바뀌게, 변경 이력 남기려고
- **예외 클래스 분리** — GlobalExceptionHandler에서 타입으로 잡아 자동으로 다른 HTTP status 응답
- **인증 일관성** — 이메일 없음 / 비번 틀림 둘 다 401로 통일하는 건 account enumeration 방지

## 헷갈리는 것

- `@RequiredArgsConstructor` — `final` 붙은 필드를 인자로 받는 생성자를 자동 생성
- `@Transactional` — SQL이 여러 줄 들어갈 때 (여러 작업이 같이 성공/실패해야 할 때 붙임)
- `BIT(1)` vs `BOOLEAN` — 아직 정확히 모름. 다음 주에 다시 정리

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
