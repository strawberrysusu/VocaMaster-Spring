# AWS Lightsail 운영

2026-09-30에 기존 HTTPS 주소를 유지하며 서울 Lightsail `vocamaster-prod`로 이전했다.
최초 이전은 운영 중이던 JAR와 DB를 복원했으므로 앱 기능 변경을 포함하지 않는다.

## 현재 구성

- Ubuntu 24.04, x86_64, 2GB RAM, 2 vCPU, 60GB SSD, swap 2GiB.
- nginx가 HTTPS를 처리하고 `127.0.0.1:8080`의 Spring 앱으로 전달한다.
- 앱·MySQL·Redis는 같은 서버의 Docker 네트워크에서 연결한다.
- MySQL과 Redis는 인터넷에 포트를 공개하지 않는다.
- 앱 768MiB, MySQL 640MiB, Redis 128MiB 제한. JVM 최대 힙은 448MiB다.
- 데이터는 `vocamaster-aws-mysql-data`와 `vocamaster-aws-redis-data` 볼륨에 저장한다.
- 실제 compose와 비밀 `.env`는 `/home/ubuntu/vocamaster-aws`에 있다.
- [compose](../deploy/docker-compose.aws.yml)는 운영 구성의 공개 사본이다. 서버 설정 변경은 별도로 검토해서 적용한다.

사용자 PC의 `ssh vocamaster-aws` 별칭은 고정 IP와 검증된 SSH 호스트키를 사용한다.
서버 재생성 시에는 호스트명 guard와 호스트키를 먼저 확인해야 한다.

## 자동 배포

GitHub Actions는 기존 테스트·프론트 lint·빌드를 통과한 master의 실행 코드 변경을 배포한다.
CI·배포 도구·문서만 바뀐 최초 이전 커밋은 현재 복원 JAR를 그대로 유지한다.
수동 실행의 `deploy=true`는 master의 앱 배포를 명시적으로 요청할 때 사용한다.
앱 배포가 대기 중일 때 CI·배포 설정을 다시 변경하면 이전 실행은 취소될 수 있다.
이 경우 최신 master 검증 후 수동 배포한다. 문서만 추가한 최신 커밋은 같은 실행 코드의 배포를 막지 않는다.

필요 설정은 `AWS_HOST`, 전용 `AWS_SSH_KEY` secrets와 공개 `AWS_SSH_KNOWN_HOSTS` variable이다.
기본 Lightsail 접속 키와 CI 전용 키를 분리한다. CI는 호스트키 검증을 생략하지 않는다.

러너가 amd64 이미지를 만들고 전송 파일의 SHA-256과 이미지 커밋 라벨을 검증한다.
[배포 스크립트](../deploy/deploy-aws.py)는 서버의 `.env`와 compose를 덮어쓰지 않고
`app`만 `--no-deps --no-build`로 갱신한다. MySQL·Redis 컨테이너 ID도 유지되는지 확인한다.
HTTP API·React 진입점·정상 인증서의 HTTPS 검사 실패 시 직전 실제 앱 이미지로 되돌린다.

Flyway는 새 앱 시작 시 DB 스키마를 바꿀 수 있다. 앱 이미지 롤백은 스키마 롤백을 의미하지 않는다.
열 삭제 등 이전 앱과 호환되지 않는 변경에는 별도 DB 복구 계획이 필요하다.
배포 로그는 서버 `deploy-logs`에 비공개로 남으며 CI 출력에 앱 로그·비밀번호를 노출하지 않는다.

## 상태 확인

```bash
ssh vocamaster-aws
cd /home/ubuntu/vocamaster-aws
sudo docker compose -f docker-compose.aws.yml ps
free -m
sudo systemctl is-active docker nginx certbot.timer vocamaster-backup.timer
sudo nginx -t
```

HTTPS 주소는 기존 `https://vocamaster-app.duckdns.org/app/` 그대로다.
DuckDNS는 도메인을 만든 **GitHub 로그인 계정**으로 관리한다.
Google 로그인으로 새로 만든 DuckDNS 계정에는 기존 도메인이 없다.

## 백업과 복구

`vocamaster-backup.timer`는 한국시간 매일 04:30에 SQL·Redis RDB·실제 실행 JAR·설정·nginx/TLS를 저장한다.
SQL 완료 표시·압축 CRC와 RDB를 검증한 후 완성된 snapshot만 발행한다.
서버의 `/home/ubuntu/vocamaster-backups`에는 최신 14개, 총 2GiB 이내로 보존한다.

이 디렉터리는 같은 서버에 있으므로 서버 자체를 잃었을 때의 외부 백업이 아니다.
PC의 복구 폴더에 있는 `Download-VocaMasterBackup.ps1`을 실행하면 별도 PC 저장본을 만들고
각 파일의 SHA-256·SQL 압축·JAR·환경 설정·TLS 포함 여부를 검증한다.
PC 다운로드는 매일 자동 실행하도록 등록되어 있지 않다. 최근 외부 보관본의 날짜를 직접 확인한다.
이 파일에는 DB·비밀번호·TLS 개인키가 들어 있으므로 Git이나 공개 저장소에 올리지 않는다.

원본 Oracle DB·서버는 삭제하지 않았고 원본 Spring 앱은 중지했다.
DNS 전환 중 오래된 IP를 사용하는 요청도 nginx가 TLS를 검증하며 AWS로 전달한다.
AWS에서 새 데이터가 저장된 뒤에는 오래된 Oracle 앱을 바로 켜면 데이터가 갈라진다.
복구할 때는 최신 AWS snapshot을 기준으로 먼저 데이터와 설정을 복원하고 주소를 연결한다.

## 이전 검증의 범위

실제 AWS HTTPS로 가입·로그인·토큰 갱신·카드 생성·학습 기록 저장 등 23개 검사를 통과했다.
최종 전환 전 원본 앱 쓰기를 멈추고 18개 테이블의 행 수·열 구조·전체 행 정규화 해시를 비교했다.
Redis 값·만료시각과 전송 파일 해시도 비교했으며 스테이징 테스트 데이터는 최종 원본 복원으로 제거했다.
재부팅 뒤 서비스 자동 시작과 외부 PC 백업도 확인했다.
검증 증거는 비밀 데이터를 포함할 수 있어 PC의 비공개 복구 폴더에 보관한다.

인증서 자동 갱신 모의 실행도 성공했다. Google 로그인 완료와 지속 부하 용량은 이 검증으로 확인하지 않았다.
새 이미지의 실제 GitHub 배포·자동 롤백은 아직 실행하지 않았다.
