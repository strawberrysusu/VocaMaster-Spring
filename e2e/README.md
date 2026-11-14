# 화면 회귀 테스트 (Playwright, ADR-055)

앱 jar를 **빈 MySQL·Redis**에 띄우고 진짜 Chromium으로 주요 흐름을 누른다.
계정·덱은 테스트가 매번 새로 만들고, 운영 서버·운영 데이터는 쓰지 않는다.

- CI: `.github/workflows/ci.yml`의 `e2e` 잡이 PR·master마다 돌고, **실패하면 배포가 돌지 않는다**.
- 무엇을 보나: 로그인 화면, 덱 화면 설명 줄·카드 추가, 학습 카드, Word 시험지 받기, 타이핑 채점, 온라인 시험 자동 ⭐·맞음으로, 폭 4개(375·820·1024·1280)에서 가로 넘침 0, 폰에서 학습 카드 한 화면, 모든 화면에서 브라우저 오류 0.
- `test.fail(...)`이 붙은 테스트는 **알려진 버그를 재현해 둔 것**(지금은 실패해야 정상). 고치는 PR에서 그 표시를 뗀다.

## 로컬에서 돌리기

```bash
# 1) 빈 DB·Redis (일회용 컨테이너, 포트는 겹치지 않게)
docker run -d --name vm-e2e-mysql -e MYSQL_DATABASE=vocamaster -e MYSQL_ROOT_PASSWORD=e2e-only -e TZ=Asia/Seoul -p 13306:3306 mysql:8.0
docker run -d --name vm-e2e-redis -p 16379:6379 redis:7.2-alpine

# 2) 앱 jar (React 번들 포함) — 또는 CI의 vocamaster-jar 아티팩트를 받아 E2E_JAR_DIR로 지정
#    Gradle 8.7은 JDK 17~21에서 돈다 — 기본 JDK가 더 새것이면 JAVA_HOME을 잠깐 바꿔서
./gradlew bootJar -x test

# 3) 테스트 (webServer 설정이 jar를 띄우고 끝나면 내린다)
cd e2e && npm ci && npx playwright install chromium
E2E_PORT=18080 \
SPRING_DATASOURCE_URL='jdbc:mysql://localhost:13306/vocamaster?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8' \
SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=e2e-only \
SPRING_DATA_REDIS_PORT=16379 SPRING_JPA_SHOW_SQL=false \
npx playwright test

# 끝나면
docker rm -f vm-e2e-mysql vm-e2e-redis
```

실패하면 `test-results/`에 스크린샷·trace가, `app.log`에 앱 로그가 남는다 (`npx playwright show-trace <trace.zip>`).
