# syntax=docker/dockerfile:1
# VocaMaster 멀티스테이지 빌드 (Phase 7 ③, ADR-043)
#
# 3단으로 나누는 이유: "빌드에 필요한 것"과 "실행에 필요한 것"은 다르다.
#  1) node:20  — React 번들만 만들고 통째로 버려짐 (CI와 같은 Node 20)
#  2) JDK 17   — jar만 만들고 버려짐 (테스트는 CI 담당, 여기선 포장만)
#  3) JRE 17   — 최종 이미지엔 실행기 + jar뿐. 빌드 도구·소스가 없으니 작고 공격 표면도 작다
# 베이스 3종 모두 멀티아치(amd64/arm64) — 로컬 x86과 Oracle A1(ARM)에서 같은 파일로 빌드된다.

########## 1) React 번들 ##########
FROM node:20-alpine AS frontend
WORKDIR /build/frontend
# 소스보다 package*.json 먼저 — 의존성이 안 바뀌면 npm ci 레이어를 캐시로 재사용
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# vite outDir이 ../src/main/...(레포 루트 기준 상대 경로) — 레포 구조를 재현했으니 /build/src/...에 떨어진다
RUN npm run build

########## 2) jar 포장 ##########
FROM eclipse-temurin:17-jdk AS backend
WORKDIR /build
COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/
# Windows에서 커밋돼 실행 비트가 없음 — CI와 같은 조치
RUN chmod +x gradlew
COPY src/ src/
COPY --from=frontend /build/src/main/resources/static/app src/main/resources/static/app
# 프론트는 1단계가 이미 빌드 → Gradle의 npm 단계는 건너뜀 (-PskipFrontend 스위치 재사용).
# 캐시 마운트: 재빌드 때 Gradle 배포판·의존성 재다운로드 방지 (최종 이미지 레이어에는 안 남음)
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon -PskipFrontend

########## 3) 실행 ##########
FROM eclipse-temurin:17-jre AS runtime
# 비root 실행 — 컨테이너가 뚫려도 root 권한이 아니게 (ADR-042 보안 게이트의 연장)
RUN useradd --system --create-home appuser
USER appuser
WORKDIR /home/appuser
# *-SNAPSHOT.jar는 부트 jar만 매치 (plain jar는 -plain.jar로 끝나 제외)
COPY --from=backend /build/build/libs/*-SNAPSHOT.jar app.jar
# 배포 산출물의 기본 프로필은 prod — dev로 뜨는 사고 자체를 봉쇄 (시크릿 검증은 ProdSafetyGuard)
ENV SPRING_PROFILES_ACTIVE=prod
# 힙은 컨테이너 메모리 limit의 75%까지 (JVM 기본 25%는 낭비) / 로그·시각은 KST
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Seoul"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
