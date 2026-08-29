# 배포 운영 가이드 (Phase 7 ⑤·⑥, ADR-045)

> 실제 IP·시크릿은 이 문서에 없다 (공개 레포). IP는 Oracle 콘솔 → Compute → Instances에서 확인.

## 서버 구성 (Oracle Cloud, Tokyo, Always Free — 월 0원)

| 서버 | 사양 | 역할 | 실행물 |
|---|---|---|---|
| `vocamaster-app` | x86 1 OCPU / 1GB / swap 2G | 앱 | `deploy/docker-compose.app.yml` (Spring, mem 700m) |
| `vocamaster-db` | x86 1 OCPU / 1GB / swap 2G | 데이터 | `deploy/docker-compose.db.yml` (MySQL 8 + Redis 7) |

- 두 서버는 같은 서브넷(10.0.0.0/24) — 앱은 DB 서버의 **사설 IP**로 접속
- Security List: 인터넷→**22만** / 3306·6379→내부망 소스만 / 앱 8080→`127.0.0.1` 바인딩 (HTTPS 전 비공개)

## 운영 원칙

1. **운영 실행은 Docker 경로만.** 직접 `java -jar`는 금지 — 기본 프로필이 dev로 떨어지는 함정 (이미지에는 `SPRING_PROFILES_ACTIVE=prod`가 박혀 있음)
2. 시크릿은 각 서버 `~/.env`에만 존재 (서버에서 `openssl rand`로 생성, 레포·채팅 무기록)
3. HTTPS(⑦) 전에는 80/443/8080을 인터넷에 열지 않는다 — 검증은 서버 내부 curl 또는 SSH 터널

## 새 버전 배포 (앱)

로컬(x86 — 서버와 아키텍처 동일)에서:

```bash
docker build -t vocamaster:prod .
docker save vocamaster:prod | gzip -1 | ssh -i ~/.ssh/vocamaster_oracle ubuntu@<APP_IP> 'gunzip | docker load'
ssh -i ~/.ssh/vocamaster_oracle ubuntu@<APP_IP> 'docker compose -f docker-compose.app.yml up -d'
```

> 서버에서 직접 빌드 금지 — 1GB라 Gradle+Node 빌드가 OOM. (Flyway 마이그레이션은 앱 기동 시 자동)

## 배포 실패 시 복구 (수동 — 자동 롤백은 백로그)

CD의 헬스 게이트·HTTP 스모크가 실패해도 **이전 컨테이너가 이미 교체된 뒤**일 수 있다. 복구 순서:

1. **증상 확인**: `ssh ubuntu@<APP_IP>` → `docker logs --tail 50 vocamaster-app-app-1`
2. **가장 빠른 복구 = 직전 커밋으로 재배포**: 로컬에서
   `git revert --no-edit HEAD && git push` → CD가 이전 코드로 새 이미지를 배포 (V 마이그레이션은 전진만 하므로 스키마는 안전)
3. **CD 자체가 죽었을 때의 비상 경로**: 서버에 남아 있는 직전 이미지로 임시 기동 —
   `docker images | head`로 이전 이미지 ID 확인 → `docker tag <이전ID> vocamaster:prod && docker compose -f docker-compose.app.yml up -d`
4. 복구 후 원인 수리 커밋을 정상 경로로 배포

## 백업 (⑥ — 매일 자동)

- **cron**: 매일 KST 04:30, DB 서버의 `~/backup-mysql.sh`
  - `mysqldump --single-transaction` → gzip → `~/backups/` (7일 롤링)
  - **교차 보관**: 앱 서버 `~/backups/`로 scp (단일 서버 장애 대비)
  - 로그: `~/backups/backup.log`
- 수동 실행: `ssh db-서버 '~/backup-mysql.sh'`

### 복구 (2026-08-26 리허설 검증됨 — 원본 16 테이블 = 복원 16 테이블)

```bash
# DB 서버에서 — 대상 DB를 지정해 부어넣는다 (본DB 복구면 vocamaster)
source <(grep MYSQL_ROOT ~/.env)
gunzip < ~/backups/vocamaster-<날짜>.sql.gz | \
  docker exec -i vocamaster-db-mysql-1 mysql -uroot -p"$MYSQL_ROOT_PASSWORD" vocamaster
```

## 점검 명령 모음

```bash
# 컨테이너 상태
ssh -i ~/.ssh/vocamaster_oracle ubuntu@<APP_IP> 'docker ps'
# 앱 내부 스모크
ssh -i ~/.ssh/vocamaster_oracle ubuntu@<APP_IP> 'curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/public/decks'
# 로컬 브라우저 미리보기 (터널) → http://localhost:8090/app/
ssh -i ~/.ssh/vocamaster_oracle -L 8090:127.0.0.1:8080 ubuntu@<APP_IP>
```

## 완료된 것 (8/26~27 갱신)

- ✅ nginx + Let's Encrypt — https://vocamaster-app.duckdns.org (자동 갱신 타이머, ADR-046)
- ✅ CD — master push → 테스트 통과 시 자동 배포 (ci.yml deploy job, docs-only 푸시는 생략)
- ✅ cron 자동 백업 첫 정기 실행 실증 (KST 8/27 04:30, backup.log 기록)

## 남은 일

- 자동 롤백 (배포 후 헬스 실패 시 이전 이미지 복귀 — 현재는 실패 알림까지만)
- 백업 오프사이트 승격 후보: Oracle Object Storage (Always Free 20GB) — 두 서버 전멸에도 생존
- A1(2c/12GB) 사냥꾼 가동 중 — 잡히면 단일 박스 이사 (별도 무료 한도라 공존)
