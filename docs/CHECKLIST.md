# VocaMaster 진행 체크리스트

> 시작일: 2026-05-04 · 목표 완료: 2027-01-04 (8개월 / ~34주)
> 마지막 업데이트: 2026-07-22

---

## 📍 현재 상태

| 항목 | 값 |
|---|---|
| **진행 중인 Phase** | **🧊 기능 동결 (8/29 선언, 아래 동결 선언 참조) — 4 학습 모드(플래시카드·퀴즈·타이핑·듣기)·공개 운영·CD·k6 실측 완비.** 이후: 유지보수 + 마감재(week note·Mustache 청소)만. 해동 조건: 11월 산업기사 후 (독해+AI 콤보 등) |
| **이번 주 집중** | 개강(8/27) 적응 + ⑥ DB 백업 절차 → ⑦ nginx·HTTPS·공개. A1 사냥꾼은 계속(잡히면 이사) |
| **전체 진행도** | Phase 0~6 ✅ (Kafka는 A안 '안 함' 결정) / **Phase 7: ①~④ ✅ + Codex P0 3건 수리 ✅ (8/26, 테스트 172)** |
| **다음 마일스톤** | 공개 후 안정 운영 — CD(자동 배포) → ⑧ k6 측정(docs/performance.md) → 구형 Mustache 정리. 운영 실행은 Docker 경로만 |
| **신규 ADR** | ADR-016~**044** — 이벤트 결합 해소·study 항·혼합 비동기·CASCADE·배포지(Tokyo 정정)·보안 게이트·Dockerfile·Compose — `docs/decisions.md` |
| **▶ 다음 액션 (resume)** | **2026-08-25 마감: README 실물화 + week-16 4차 인출 + Phase 7 ① CI ② 보안 게이트 + Boot 3.5.16 상향 (테스트 169, ADR-041·042)**. ②의 실물: prod에서 springdoc 완전 비활성(404) / `ProdSafetyGuard`(레포에 공개된 dev·test 시크릿 재사용, 32바이트 미만 → 부팅 거부) / `ProdProfileTest`(prod 컨텍스트를 CI마다 실부팅 — 환경변수 4종 해석·Swagger 닫힘·Redis 없이 fail-open 부팅) / OSIV off / 기본 계정 자동구성 제외. 의존성: Boot 3.3.0(OSS 지원 종료 라인) → 3.5.16 + springdoc 2.8.17 + jjwt 0.12.7 — 사상자 전수 1건(Hibernate 6.6의 '삭제된 엔티티 참조' flush 단속 — 테스트에 영속성 컨텍스트 경계 `em.clear` 재현으로 수리, 운영 무관). dev 스모크는 8081 bootRun으로 7종 실측(8080 실서버 무접촉 룰 준수). **같은 날 저녁 ③ Dockerfile까지 완료** (ADR-043 — 3단 멀티스테이지·비root·기본 prod·565MB, 컨테이너에서 안전핀 거부+스모크 4종 실증). **같은 날 밤 ④ compose까지 완료** (ADR-044 — prod 스택 별도 파일·프로젝트 분리, DB/Redis 내부 전용, 리허설 실측: 처녀 DB Flyway 15판→가입→로그인→인증 왕복, 3컨테이너 healthy, 정리 후 dev 무영향). **다음 세션 첫 일감 = Phase 7 ⑤ Oracle A1 인스턴스 확보·배포** — 사용자가 직접 하는 단계(계정·카드 등록)라 **화면 단위로 하나하나 안내 약속**. 서버에서: git clone → .env 작성(openssl rand) → `docker compose -f docker-compose.prod.yml up -d --build` 한 줄. 그 뒤 ⑥ 백업 → ⑦ nginx·HTTPS → CD |
> **🎓 Phase 4 완전 졸업 (2026-08-11)** — 실서버 HTTP 데모(검색→복사→좋아요→popular 1위 반영→400/403) + 구두 3문 통과. 1주 만에 Phase 통째 완료(계획은 '개강 전 절반'). ADR-030~033, 테스트 105+, 면접 1급 재료: FK 데드락 실화(ADR-031)·잠금 순서 3회 학습·조작 방지 3계열(복사 제외/좋아요 1회 캡/학습 보류). 다음: ① **week note 복구** (최고가 밀린 항목 — Phase 4 노트에 데드락 스토리) ② 주말 폐쇄훈련 #3 3회차 (빈칸 채우기, Codex 닫기 체크) ③ **Phase 5 Redis 진입** (사전 설명부터. 인기 정렬 filesort → ZSET 전환이 첫 동기). ⚠️ 터미널 gradlew 죽으면 JDK 25 Lombok 함정 / 데모 서버 아직 켜져 있으면 gradle bootRun 태스크 종료 개강 전 목표: **Phase 4 MUST 절반** (주3 8/17~은 국비 행정으로 물량 축소). 밀린 것: week note(최고가) + 폐쇄훈련 #3 3회차(빈칸 채우기, 주말 — 시작 전 Codex 닫기 체크). ⚠️ 터미널 gradlew 죽으면 JDK 25 Lombok 함정 참조 |

---

## 🧊 재동결 (2026-09-01) — 1회 해동 후 복귀

**해동 사유**: 사용자가 매일 쓰며 겪은 학습 화면 핵심 불편 3건 — 🔊가 카드 우상단 구석 / 뒤집기 단방향 / **이전 카드 답 수정 불가**. 세 번째는 UI 손질이 아니라 저장 계약 문제였다(즉시 저장 구조에서는 되돌리려면 Leitner 박스를 되돌려야 하는데 오답이 box 1로 풀 리셋이라 이전 값이 안 남는다).

**한 일** (ADR-050): 세션 중 답을 프론트 임시 상태로 두고 완료 시 `POST /reviews/answers/batch` 한 트랜잭션 반영. 멱등은 `review_submissions`(V21)의 `(user_id, submission_id)` unique + payload SHA-256. 최초 POST 직전 payload 동결로 재시도가 이미 반영된 카드를 다시 세지 않게. 죽어 있던 `StudySession` 부활은 YAGNI로 철회.

**검증**: 테스트 189개(신규 9, 실제 스레드 2개 동시 제출 포함) / 로컬 브라우저 스모크 9종 / 응답 유실·막다른 골목 시나리오를 덱·due 두 모드에서 재현 후 탈출 확인 / 운영 배포 후 실사용 스모크 + 테스트 데이터 정확 원복(`study_count` 106→99, streak 5 불변).

**PR**: #1(일괄 제출), #2(뜻 안 보고 채점) — 둘 다 CI 그린 후 squash merge, CD 자동 배포.

**재동결 범위**: 08-29 선언과 동일. 새 기능 추가 중단, 버그 수리·보안 패치는 계속.

**다국어(i18n) — 안 하기로 선택 (2026-09-01 판단, 9월 말 재검토)**

규모 실측: 프론트 화면 문구 약 555줄(주석 제외) + 백엔드 사용자 메시지 246건(예외 73개) + privacy.html + README 117줄. 최소 3~4일.

지금 안 하는 이유: ① 프론트만 번역하면 일본어 화면에 한국어 서버 에러가 섞여 완성도가 되레 떨어진다 ② 그 3~4일이면 일본어 모의면접 4회 + 코테 15~20문제 + 기술 질문 20개 인출이 가능하다 ③ 10월 면접이 목표면 우선순위가 아니다.

**대상 사용자 관점에서도 한국어 UI가 틀리지 않다** — 이 앱의 첫 사용자는 일본어·영어를 공부하는 한국인(개발자 본인)이다. 일본 회사는 이 앱을 사러 오는 게 아니라 문제 발견과 설계를 본다.

면접 답변으로 정리:
> 現在は、私自身を含む韓国人学習者を最初の対象としているため、韓国語UIを優先しました。多言語化には、フロントエンドの文言分離とAPIエラーコードの標準化が必要なので、今後の拡張課題として整理しています。

할 때의 방법은 이미 정해져 있다: ① 화면 문구를 `ko.json`/`ja.json`으로 분리 ② 백엔드는 메시지 대신 code를 내려보내고 프론트가 번역 — `SUBMISSION_MISMATCH`에서 이미 한 방식을 73개 예외로 확장 ③ **브라우저 언어 자동 선택 + 수동 전환** (일본어 기본 고정 아님 — 한국 회사 지원 시 오히려 불리).

재검토 조건(9월 말): 일본어 이력서 제출 가능 / 자기소개·지원동기 말하기 가능 / VocaMaster 핵심 질문 10개 답변 가능 / 모의면접 3회 이상 / 코테 루틴 정착. 이게 다 되고 시간이 남으면 착수.

**이번에 만들어진 백로그** (동결과 무관):
- `review_submissions.created_at`이 `NOW(6)`(컨테이너 MySQL = UTC)라 KST 통일 원칙 이탈. 운영 실측 확인(영수증 06:06:09 UTC / 진행도 15:06:09 KST). 읽는 코드가 없어 기능 영향 없음. 영수증 정리 기능을 만들 때 서비스에서 KST now를 한 번 만들어 영수증·진행도·통계가 공유하도록 수정
- 백업 스크립트가 비밀번호를 명령줄 인자로 전달(`ps` 노출) → `--defaults-extra-file`
- 카드 조회 IN 배치 최적화, 오래된 영수증 정리

---

## 🧊 기능 동결 선언 (2026-08-29) — 정본 (이 문서에서 이 한 곳만)

**동결 조건 전부 충족 후 확정, Codex 최종 검산 "동결 가능" 판정 (2026-08-30):**
- 보안: 구글 선점(pre-hijacking) 차단 · 탈퇴 정합(JWT 즉시 차단·공개 콘텐츠 숨김·복사/좋아요 우회 404·캐시 count/rebuild 제외)
- 데이터: V19 임포트 오염 **7,641장** 수리(A형 2,951 + B형 4,690) — prod 실측 잔존 0(정확형·느슨형 모두).
  V19 주석의 "3,369/4,755"는 COLLATE 없는 진단 쿼리가 반각 주석 483건을 오산입한 수치 — **ADR-049 정오표** 참조
- 안정화: import 원자성(단일 트랜잭션 API) · update @Transactional · PATCH @Valid · CI push 전체 diff
- 마지막 기능: 📁 폴더(V20, SET NULL) + 덱 일괄 삭제 — 최종 테스트 **180** 전부 그린

**동결의 의미**: 새 기능 추가 중단. 버그 수리·보안 패치는 계속. 해동 조건은 기존 결정 유지 —
독해+AI 콤보(11월~), 댓글(활성 사용자 10명+), 타자 속도·디자인 리뉴얼(재건축 시즌에 재평가).
이후의 개발 시간은 코드가 아니라 **흡수**에 쓴다: 핵심 6흐름 재구현 · 코테 · 면접 설명 반복.

**알려진 비차단 백로그** (동결과 무관, 버그 수리 범주):
- 일괄 삭제·이동 중 네트워크 실패 시 이미 성공한 덱 ID가 선택 상태에 남을 수 있음 (재시도하면 404 — 파괴 없음)
- ~~퀴즈·타이핑 🔊 버튼은 채점 전에도 읽기를 재생~~ → **유지 확정 (8/30 사용자 결정)** — 직접 눌러야 나오는 소리는 스포일러가 아니라 선택적 듣기 힌트
- 자동 롤백 · Object Storage 오프사이트 백업 · week-17 note
- (Codex UI 검산 8/30 잔여) 사이드바·Home·Decks의 `/decks`·`/folders` 중복 호출 + DeckService 카드 수 N+1 — 현 규모(덱 100)에선 무해, 규모가 생기면 통합 응답으로 · 회색 보조 글자 대비 소폭 낮음 · 프론트 자동 UI 테스트 부재(인증 후 화면은 수동 스모크)
- 🧊 해동 시 1순위 후보: 내 덱 카드의 덱별 진행바("N% 익힘"·복습 대기) — 덱별 집계 API 필요 (리디자인 목업의 미이식분)

### Codex 전수조사 (2026-09-01) — 실측 검증 후 크기·사유와 함께 미룬 것

주장 5건 전부 코드로 확인됨(CONFIRMED). 이 중 privacy 링크만 수리하고 나머지는 아래에 기록만 한다.
**미루는 근거는 "안 중요해서"가 아니라 "9월 마감이 밖에 있는 일(일본어 서류·10월 면접)이 먼저"이기 때문.**

| 항목 | 실측 크기 | 동결 위반 | 판정 |
|---|---|---|---|
| ① privacy 도달 경로 부재 | 0.5~1h | ✗ (마감 누락) | **9/1 수리 완료** |
| ② **fail-closed 가져오기**(아래) | 2~4h + | ✗ (버그) | 미룸 |
| ③ 파일 가져오기 미리보기·수동 구분자 | 반나절~하루 | ✓ | 미룸 |
| ④ `lastReviewedAt` DTO 노출 | small~medium | ✓ | **하지 않음** |
| ⑤ 기기·세션 관리 | **2~4일** | ✓ | 미룸 (9월 착수 금지) |

- **② fail-closed 가져오기** (이름 주의 — Codex 정정 9/1: "detectSeparator 강화"로 적으면 안 된다.
  구분자가 애매한 줄은 **감지 알고리즘을 영리하게 만드는 것만으로 해결되지 않는다**. 방향은 정확도가 아니라 **거절**이다.)
  현상: `ImportService.java:158-177` — 후보 고정 순서(탭→|→:→,→-), 표본은 **비어있지 않은 앞 5줄뿐**,
  과반 미달 시 **하이픈으로 강행**(:35, :176). `shapeOk`(:141)가 조각 수 2·3이면 통과시켜
  **오분할이 failed로 보고되지 않고 정상 카드로 저장된다** (4조각 이상일 때만 실패).
  실측 재현(감지·파싱 로직 복제 실행): `"mother-in-law 시어머니"` → front=`mother` / reading=`in` / back=`law 시어머니` **SAVED**.
  해야 할 것 4개 (한 세트):
    1. **확신 없으면 저장하지 않는다** — 과반 미달 시 하이픈 폴백 제거하고 실패로 거절
    2. **수동 구분자 선택** (ImportCards엔 이미 있음, 파일 화면에만 없음)
    3. **등록 전 미리보기** (③과 같은 작업)
    4. **CSV 지원 범위 명시** — 일반 CSV를 표방하면 따옴표·이스케이프까지 처리하는 진짜 CSV 파서가 필요하다.
       그럴 생각이 없으면 화면에 "단순 구분 텍스트"라고 못 박는다
  회귀 테스트 설계가 비용의 대부분 (현 `ImportServiceTest.java:95-107`은 정상 케이스만 검증).
  **그 전까지 낯선 CSV·텍스트는 미리보기가 있는 '일괄 붙여넣기'(ImportCards)로 넣는다.**
- **③ 미리보기** — 새 API 불필요. 기존 `POST /import/preview`(`ImportController.java:21-25`) 재사용 가능.
  비용은 다중 파일 × 미리보기의 상태 관리(파일별 preview 캐시·stale 판정·부분 확정).
- **④ 하지 않음** — 화면에 쓸 계획이 없다. 프론트는 이미 내려오는 `nextReviewAt`·`boxLevel`조차 선언 없이 버린다(`Study.tsx:10-16`).
  쓰는 데 없이 응답 계약만 넓히는 순수 순증.
- **⑤ 기기 관리** — RefreshToken에 user_agent·last_used_ip는 저장되지만 **조회·개별 폐기 API가 없다**(전체 로그아웃만 존재).
  리포지토리 쿼리 2 + DTO 신설 + 엔드포인트 2 + IDOR 소유권 검증 + 통합 테스트. "현재 기기" 표시는
  `AuthController.java:27` `REFRESH_COOKIE_PATH="/auth"` 때문에 인증 설계 결정으로 번진다. **화면만 붙이는 작업이 아니다.**

> ⚠️ **동결 운영 규칙 (9/1 추가)**: 동결 선언은 8/29·8/30·9/1 세 번 나왔으나 그 사이 코드 커밋이 한 번도 멈추지 않았다
> (9/1 15:41 재동결 선언 → 16:01·17:00 feat 커밋 2건). 사유를 적으면 통과하는 구조라 **선언문 추가는 효과가 없다.**
> 브레이크는 문구가 아니라 숫자로 둔다.
>
> | | 내용 |
> |---|---|
> | **기간** | **9월 한정** (10/25 산업기사 실기 + 10월 면접까지). 11월에 재평가 |
> | **상한 대상** | "있으면 좋은 것" = 기능 추가·개선 → **주당 PR 1개, 항목당 타임박스 1시간** |
> | **상한 밖** | **보안 · 데이터 유실 · 서비스 장애 → 즉시 수리, 개수 무관** |
> | **상한 밖 (추가 9/1)** | **오표기 정정** — 화면 문구가 실제 동작과 달라 사용자를 오해시키는 것. 로직 무변경·문자열만. 판정 기준은 '간단해 보인다'가 아니라 **실측**(변경 라인 수·로직 변화 유무) |
>
> (9/1 사용자 확정: 상한만 두면 진짜 급한 게 왔을 때 막힌다는 지적을 수용해 예외를 명시.
> 반대로 "상황이 어찌될지 모르니 제한하지 말자"는 논리는 지난 세 번의 동결을 무력화한 그 논리와 같은 모양이라 상한 자체는 유지한다.
> 오늘 privacy 링크는 컴플라이언스 공백이었으므로 원래 '상한 밖'에 해당했다.)

**다음 주 PR 후보 (9/8~, 상한 1개 안에서)**
- ~~**복습 화면 🔊 버튼 문구 오류**~~ → **9/1 정정 완료** (`Study.tsx` 라벨 "다시 듣기" → "발음 듣기").
  이 화면엔 자동 재생이 없어(`speak()` 직접 호출 0건, 카드 클릭은 `setRevealed` 뒤집기만) "다시"가 오해를 샀다.
  SpeakButton의 `aria-label`·`title`이 이미 "발음 듣기"였으므로 이제 셋이 일치한다.
  (참고: `Listening.tsx`의 "다시 듣기"는 **자동 재생이 실제로 있으므로 정확한 표현** — 손대지 않는다.)
- 같이 판단할 것: **카드 등장 시 자동 재생 여부.** 넣으면 새 기능이고, 브라우저 autoplay 정책상
  사용자 제스처 전에는 차단돼 **첫 카드만 조용히 실패**하는 어중간한 동작이 될 수 있다. 문구 수정과 분리해서 결정.
  **참고(9/1 사용자 실측): Quizlet도 같은 구조** — 카드 클릭/Space는 뒤집기, 🔊는 별도 버튼, 자동 재생은 **설정 옵션**.
  즉 현재 동작 자체는 업계 표준과 같고 **틀린 건 문구 하나뿐**. 자동 재생을 넣더라도 항상 켜짐이 아니라 설정 토글로.

---

## 📖 사용법

### 체크박스
- `[ ]` 미완료 → 끝나면 즉시 `[x]`로 변경하고 커밋
- **커밋 단위**: 한 체크박스 또는 *논리적으로 묶인 작은 단위* = 한 커밋
  (예: `application-{dev,test,prod}.yml` 분리는 묶어서 한 커밋 OK)

### 모드 (Mode)
어떤 방식으로 만들지를 표시. 섹션 또는 항목 단위.
- 🟢 **A** = 내가 직접 짜고 Claude는 가이드 + 리뷰
- 🔵 **B** = Claude와 한 줄씩 페어 (면접 단골 영역)
- ⚪ **C** = 데모 받고 닫고 다시 짜기 (처음 보는 기술)

### 우선순위 (Priority)
완수 의무도. 항목 단위로 표기.
- **[MUST]** — 이거 없으면 프로젝트 핵심이 약해짐. 무조건 한다.
- **[SHOULD]** — 있으면 좋고, 없어도 프로젝트는 성립.
- **[STRETCH]** — 시간 남으면 보너스. 못 해도 OK.

> Phase는 **MUST 전부 + SHOULD 절반 이상**이면 완료로 봄. STRETCH는 *완전히 무시 가능*.

### 운영 규칙
- **90분 룰**: 한 작업이 90분 넘게 막히면 무조건 도움 요청. 혼자 끙끙 X
- 새 기능 떠오르면 해당 Phase 하단 `### 🆕 추가 아이디어`에 적기. 본 리스트에 즉시 끼워넣지 말 것
- Phase 진행 중에 다른 Phase 항목 손대지 말 것 (한 우물만)

---

## 🤖 AI 사용 규칙 (2026-05-08 확정)

> 이 체크리스트의 목표는 체크박스를 다 채우는 게 아니라, 8개월 뒤 핵심 코드를 *내가 설명하고 고칠 수 있게* 되는 것.

### 🟢 운영 모드 (필수 준수)

**핵심 코드 (Service/Controller/Entity/테스트)는 사용자가 직접 타이핑한다.**

- Claude는 **어디에 / 무엇을 / 어떻게**의 3가지로 안내만 (Write/Edit 자제)
- 사용자가 *손으로* 코드 친 후 막힐 때만 Claude가 직접 수정
- 패턴이 헷갈리면 Claude가 *예시 한 블록* 보여줌 → 사용자가 *손으로* 옮겨치며 이해

**이유:** "AI가 다 짜고 사용자는 읽기만"하면 NewsPick 반복 — 면접에서 무너짐. 손가락이 한 번 친 후에 이해가 더 깊어짐.

### ⚪ Claude가 처리해도 OK인 영역

- yml / build.gradle / .env.example 같은 보일러플레이트 설정
- Flyway 마이그레이션 SQL (가이드는 함께)
- 단순 import 정리, 변수명 변경
- git 명령, CHECKLIST 갱신

### 적극 사용해도 되는 영역

- 설계 대안 제시, 트레이드오프 정리
- API/테이블/테스트 케이스 목록 뽑기
- 에러 원인 분석, 코드 리뷰
- README/문서 다듬기, 면접 질문 만들기

### 금지 사항

- ❌ AI가 짠 코드를 *읽지 않고* 바로 커밋
- ❌ 에러 메시지만 던지고 수정 코드 그대로 복붙
- ❌ "왜 되는지 모르는데" 다음 단계로 넘어가기
- ❌ "이 기능 전체 만들어줘" 같은 통째 요청
- ❌ Claude가 핵심 코드를 Write/Edit로 박고 사용자가 "옮겨치기"만 하는 패턴

### 알려진 함정

- **`application.yml`(main)에 새 키 추가 시 → `src/test/resources/application.yml`에도 *반드시* 같은 키 추가.** 두 파일이 따로 관리됨. 안 하면 PropertyPlaceholderHelper IllegalArgumentException 발생 (이미 두 번 당함)
- **Docker Desktop 업데이트 후 Testcontainers 전멸 (`Could not find a valid Docker environment` + 400)** → 엔진이 구식 API를 거부하는 것. Testcontainers 1.21.3은 API v1.32로 요청하는데 Docker 29.x 엔진의 최소 지원은 1.40. **해결: `~/.docker-java.properties`에 `api.version=1.44` 한 줄** (2026-07-06 당함. env `DOCKER_API_VERSION`이나 `.testcontainers.properties`의 `api.version`은 안 먹힘 — 반드시 `.docker-java.properties`)
- **터미널에서 `gradlew` 실행 시 Lombok 크래시 (`ExceptionInInitializerError` at `LombokProcessor.init`)** → 시스템 JAVA_HOME이 JDK 25를 가리키는데 Lombok이 JDK 25 미지원. IntelliJ Gradle 창은 temurin-17을 써서 멀쩡하니 "IDE는 되는데 터미널만 죽는" 형태로 나타남. **해결: `~/.gradle/gradle.properties`에 `org.gradle.java.home=C:/Users/qjwkr/.jdks/temurin-17.0.19` 한 줄** (2026-07-20 당함)
  - **같은 지뢰의 IntelliJ 버전**: Gradle Sync 실패 — `Incompatible Gradle JVM` / `Unsupported class file major version 69` / "incompatible Java 25.0.3 and Gradle 8.7". IntelliJ의 Gradle JVM 드롭다운이 JAVA_HOME(JDK 25)을 가리켜서 발생. **해결: Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JVM = temurin-17 선택 → 재싱크.** Gradle 9.3 업그레이드 제안은 무시 (Phase 중간에 빌드 도구 업그레이드 금지) (2026-07-22 당함)

### 📜 ADR (Architecture Decision Record) 정책

- 새 기능/기술 도입 *전*에 `docs/decisions.md`에 ADR 추가 (또는 `docs/decisions/ADR-NNN-제목.md` 분리)
- Claude는 *코드 안내 전에* 항상 "왜 이걸로 가는가 / 대안은" 제시 후 사용자 결정 받기
- 양식: 상태 / 범위 / 컨텍스트 / 대안 3개+ / 결정 / 근거 / 트레이드오프
- 5~10개 누적되면 디렉토리 분리 (한 파일 → 개별 파일)
- 현재 ADR 19개 누적 — `docs/decisions.md` 참조

### 🔍 작업 시작 전 Claude 점검 루틴 (필수)

새 작업 시작 시 Claude는 다음 6단계를 *반드시* 훑고 사용자에게 짧게 보고 후 진행:

1. **현재 상태 확인**
   - `git status` (작업 트리 clean한지)
   - `git log --oneline -3` (최근 커밋)
   - CHECKLIST 헤더의 "진행 중인 Phase" / 진척도

2. **이 작업의 범위 + 의존성**
   - CHECKLIST의 해당 Phase 어느 항목인지 *콕 짚기*
   - 선후 작업 (이거 끝나야 뭐 가능한지 / 이거 전에 뭐 필요한지)

3. **관련 ADR 참조**
   - 이 작업의 결정 근거가 ADR-NNN에 있나? → 있으면 *참조 한 줄*로 요약
   - 없으면 *지금 결정해야 하나?* → 대안 3개+ 제시 후 사용자 결정 받기

4. **알려진 함정 점검**
   - `application.yml` 변경? → `src/test/resources/application.yml` 동기화 짚기
   - 마이그레이션 추가? → V1~V4는 *절대 수정 X*, 새 V_N만 추가
   - 새 엔티티 필드? → DTO + Service + Controller + 테스트까지 영향 추적
   - 예외 던지는 곳? → 새 커스텀 예외 사용 (옛 `ResponseStatusException` X)
   - JPA `@Modifying` 쿼리? → `flushAutomatically + clearAutomatically` 박기

5. **운영 모드 확인**
   - **핵심 코드** (Service/Controller/Entity/Repository/테스트) = 🟢 **사용자 직접 타이핑**
   - **보일러플레이트** (yml, build.gradle, DTO, 단순 import) = ⚪ Claude OK
   - **React** (도입 시) = ⚪ Claude 작성 + 사용자 읽기 (ADR-016 예외)
   - **Flyway SQL** = 🟡 사용자가 짜되 Claude가 형식/스타일 가이드

6. **새 결정이면 ADR 먼저**
   - 코드 손대기 *전에* — 대안 3개+ 검토 → ADR 작성 → 사용자 결정 → 코드 작업
   - "대안 1개만 떠오르면 아직 고민 부족"

> Claude가 이 6단계를 *생략하고* 코드부터 던지면 사용자가 "점검 루틴 빠뜨림"이라고 지적할 것. 강제 규칙.

### 🎓 학습 단위 통과 의식

테스트/개념/기능 *하나를 넘어가기 전*, Claude는 그 단위의 **핵심을 꿰뚫는 확인 질문 1개**를 던진다.

- 단순 "이해됐어?" ❌ → 사용자가 *직접 답해야 하는* 질문 ⭕ (active recall)
- 사용자가 답하면 → 이해 검증 → 통과 / 막히면 → 그 부분 다시 설명
- 질문은 *그 단위의 본질*을 건드릴 것 (지엽적 X)
- 학습의 *통과 기준* = 질문에 답하는 것

### 🎯 작업 완료 후 Claude 확인 루틴 (필수)

핵심 코드 작업 (Service/Controller/Entity/Repository/Tests) 완료 시, Claude는 다음 순서를 *반드시* 실행:

1. **컴파일/테스트 그린 확인**
2. **핵심을 꿰뚫는 질문 1~2개** 제시
   - WHAT (무엇을 바꿨나) ❌ — 너무 쉬움, 코드 보면 답 나옴
   - WHY (왜 이렇게? 다른 방식이면?) ✅
   - TRADE-OFF (이 선택의 비용은?) ✅
   - 연결 (이 변경이 다른 코드와 어떻게?) ✅
   - 가설 (이거 안 했으면 어떻게 망가지나?) ✅
3. **사용자 답 → Claude 평가**
   - 답이 핵심 짚음 → 통과, 다음 작업
   - 답이 빈약 → *어디가 부족한지* 짚고 → 보강 설명 → 재질문
   - "모르겠어" 정직 답 → 설명 → 잠시 후 재질문
4. **답 OK된 후에만 commit / 다음 작업 진입**

#### 적용 범위
- ✅ Service / Controller / Entity / Repository / 핵심 테스트
- ❌ yml / build.gradle / DTO 단순 추가 / 보일러플레이트 (생략 OK)
- ❌ CHECKLIST / ADR 갱신 (생략 OK)

#### "핵심을 꿰뚫는" 질문 예시 (참고)
- "왜 X 대신 Y? 단일 클래스만 둬도 되는 거 아냐?"
- "이 메시지를 박았는데, 정상 흐름에서 그게 어떻게 발생할 수 있나?"
- "이거 안 했으면 어떤 HTTP status가 나왔을까?"
- "이 변경이 다른 컴포넌트(예: ExceptionHandler)와 어떻게 연결되나?"

> Claude가 작업 끝났는데 질문 안 던지고 다음 작업 진입하면 사용자가 "확인 루틴 빠뜨림"이라고 지적할 것. 강제 규칙.

**핵심 기능(B 모드) 작업 루틴**
1. 내가 요구사항 5줄 작성
2. 내가 API 경로 / Request·Response 예시 / 테스트 케이스 목록 작성
3. AI에게 "코드 주지 말고 설계만" 요청
4. 내가 Entity → Repository → Service → Controller 직접 구현
5. 막히면 질문 (90분 룰)
6. 완성 후 AI 리뷰 ("문제점만 먼저, 수정 코드는 그다음")
7. 핵심 기능은 최소 1개 테스트를 *내가 직접* 작성

---

## 🎤 설명 가능 기준

핵심 기능은 아래 5개를 만족해야 진짜 "완료"로 본다.

- [ ] 왜 만들었는지 (요구사항/동기) 설명 가능
- [ ] 요청 → Controller → Service → Repository → DB 흐름을 그림으로 설명 가능
- [ ] 주요 예외 케이스 3개 이상 설명 가능
- [ ] 테스트 케이스 2개 이상 직접 설명 가능
- [ ] 개선 전/후 또는 트레이드오프 설명 가능 (왜 X 안 쓰고 Y 썼는지)

> 4개 이상 만족 → 내 코드. 2개 이하 → 아직 AI 코드.

---

## 📆 매주 완료 기준

매 주말에 자가 점검.

- [ ] 이번 주 체크박스 최소 2개 완료
- [ ] 테스트 깨진 상태로 주말 넘기지 않기
- [ ] `docs/notes/week-N.md` 학습 노트 작성
- [ ] 새로 생긴 아이디어는 "추가 아이디어"에만 기록 (본 리스트 침범 X)
- [ ] 다음 주 작업 우선순위 재정렬

---

## 📅 일정 / 버퍼 (목표 ~34주)

| 구간 | 기간 | 누적 |
|---|---|---|
| Phase 0 — 부트스트랩 | 1~2주 | 2주 |
| Phase 1 — 인증 강화 | 4주 | 6주 |
| Phase 2 — CRUD + 학습 모드 | 4주 | 10주 |
| **버퍼** | **1주** | **11주** |
| Phase 3 — 반복 학습 알고리즘 | 4주 | 15주 |
| Phase 4 — 공개 단어장 / 공유 | 4주 | 19주 |
| **버퍼** | **1주** | **20주** |
| Phase 5 — Redis | 3주 | 23주 |
| Phase 6 — 비동기 이벤트 | 3주 | 26주 |
| Phase 7 — 배포 / 성능 / 관측 | 4주 | 30주 |
| Phase 8 — 마감 / 면접 준비 | 3주 | 33주 |

> 시험/과제/번아웃/배포 사고 등 예상 못한 일은 *반드시* 생김. 버퍼 주차에 미뤄둔 작업 처리 또는 휴식.
> 30주에 끝나면 Phase 8을 늘려서 더 깔끔하게 마감.

---

## ✅ MVP 베이스 (이미 완료, 2026-05 이전 작업)

<details>
<summary><b>인증/회원</b> — 4 items</summary>

- [x] 회원가입 API (`POST /api/auth/register`)
- [x] 로그인 API (`POST /api/auth/login`) — JWT 단일 토큰 7일
- [x] JWT 인증 필터 (`JwtAuthFilter`)
- [x] Spring Security 기본 설정 (`SecurityConfig`)

</details>

<details>
<summary><b>Deck / Card</b> — 6 items</summary>

- [x] Deck 엔티티 (User 1:N, Card 1:N)
- [x] Deck CRUD + 소유권 검증 (`DeckService.verifyOwner`)
- [x] Card 엔티티 (front/back/starred)
- [x] Card CRUD + 별표 토글
- [x] Card 페이지네이션 조회
- [x] Card 텍스트 일괄 등록 (`ImportService`) + preview

</details>

<details>
<summary><b>Quiz / Study</b> — 6 items</summary>

- [x] 5지선다 퀴즈 생성 (`QuizService.generate`)
- [x] 서버 측 정답 검증 + `QuizAttempt` 저장
- [x] 퀴즈 이력 조회
- [x] 학습 세션 시작 (`StudyService`)
- [x] 안다/모른다 기록 (`StudyRecord`)
- [x] 덱별 통계 API

</details>

<details>
<summary><b>인프라/UX</b> — 5 items</summary>

- [x] Mustache UI 데모 (회원가입/로그인/덱/카드/퀴즈/학습)
- [x] 한글 인코딩 설정 (UTF-8 force)
- [x] Swagger UI (`/api-docs`)
- [x] `GlobalExceptionHandler` 기본형
- [x] 서비스 테스트 (auth, card, import, quiz, study) — H2 기반

</details>

---

## 🟢 Phase 0 — 부트스트랩 (1~2주)

> **목표:** "내가 이해할 수 있는 안정적인 백엔드 뼈대"로 재정비.
> 새 기능 X. 기존 코드 정돈 + 운영 가능한 형태로 변환.
> **모드:** 🟢 A (전부 직접)

### ⚠️ Flyway 안전 규칙 (작업 전 필독)
- [x] **[MUST]** 한 번 적용한 `V*.sql`은 **절대 수정 금지** (checksum mismatch 발생)
- [x] **[MUST]** 변경이 필요하면 새 `V{n+1}__변경.sql` 파일 추가
- [x] **[MUST]** 로컬 DB는 마이그레이션 시작 전 drop 후 재생성
- [ ] **[SHOULD]** `docs/migration-rule.md` 작성 (이 규칙 + 실수 사례)

### 📝 문서
- [x] **[MUST]** `README.md` — 서비스 소개 / 기술 스택 / 실행 방법 / 진행 상태 *(초안 작성 완료, Phase 진행하며 갱신)*
- [ ] **[SHOULD]** `docs/ROADMAP.md` — 8개월 로드맵 요약 (의도/이유 위주)
- [ ] **[SHOULD]** `docs/ERD.md` — dbdiagram.io 코드 + 캡처 이미지
- [ ] **[SHOULD]** `docs/notes/week-1.md` — 학습 노트 첫 주 시작

### ⚙️ 설정 분리 + 환경변수화
- [x] **[MUST]** `application.yml` 공통 설정만 남기기
- [x] **[MUST]** `application-dev.yml` 분리 (로컬 MySQL, show-sql=true)
- [x] **[MUST]** `application-test.yml` 분리 (H2, ddl-auto=create-drop)
- [x] **[MUST]** `application-prod.yml` 분리 (env vars only, ddl-auto=validate, show-sql=false)
- [x] **[MUST]** DB url/username/password → `${DB_URL}` 등으로 추출 (prod만, dev는 yml에 직접)
- [x] **[MUST]** JWT secret → `${JWT_SECRET}` 추출 (prod만, dev는 yml에 직접)
- [x] **[MUST]** `.env.example` 작성 (실제 `.env`는 이미 `.gitignore`에 등록됨)
- [ ] **[SHOULD]** README에 "로컬 실행 시 환경변수 설정 방법" 보강 (prod 배포 시점에)

### 🗄️ Flyway 도입
- [x] **[MUST]** `build.gradle`에 `flyway-core` + `flyway-mysql` 추가
- [x] **[MUST]** `src/main/resources/db/migration/` 디렉토리 생성
- [x] **[MUST]** `V1__init_schema.sql` — 현재 엔티티 직접 SQL로 작성
- [x] **[MUST]** `application.yml`에 Flyway 설정 추가 (dev: enabled, test: disabled)
- [x] **[MUST]** dev profile에서 `ddl-auto: validate`로 변경
- [x] **[MUST]** 로컬에서 마이그레이션 한 번 돌려서 검증 (DB drop → recreate → bootRun → V1 적용 + JPA validate 통과)
- [x] **[SHOULD]** test profile은 `ddl-auto=create-drop` 유지 (Flyway off)
- [x] **[보너스]** 6개 테스트 클래스에 `@ActiveProfiles("test")` 명시 (default profile=dev로 떨어져 H2에 MySQL용 V1이 실행되던 문제 해결)

### 🧹 코드 정리
- [ ] **[SHOULD]** `BaseTimeEntity` 추가 (`createdAt`, `updatedAt`) — 모든 엔티티 상속 (다음 Phase에서)
- [x] **[MUST]** `ErrorResponse` DTO 통일 형식 정의 (status/code/message/timestamp)
- [x] **[MUST]** `GlobalExceptionHandler` 보강 — `NotFoundException`, `ForbiddenException`, `BadRequestException`, `UnauthorizedException` 분리 + 보안 일관성 (이메일 없음/비번 틀림 모두 401)
- [x] **[MUST]** 서비스 코드의 `ResponseStatusException`, `IllegalArgumentException` 사용처 정리 (11건 교체 + legacy 핸들러 제거 + 9개 테스트 assertThrows 갱신)
- [ ] **[STRETCH]** 패키지 구조 점검

### 📓 학습 노트
- [x] **[SHOULD]** `docs/notes/week-1.md` 작성 (한 일 / 이해한 것 / 헷갈리는 것 / 면접 질문 3개)

### ✅ Phase 0 완료 기준
- [x] 모든 MUST 항목 완료
- [x] `gradlew bootRun`으로 dev profile 정상 실행
- [x] `gradlew test` 그린 (24/24)
- [x] `application-prod.yml`에 비밀 정보 0개 (전부 env var)
- [x] Flyway로 빈 DB → 현재 스키마 재현 성공
- [x] README에 환경변수 설정 안내 반영

### 🆕 추가 아이디어
*(공란)*

---

## 🔵 Phase 1 — 인증 강화 (4주)

> **목표:** 신입 백엔드 면접에서 통할 인증 구조.
> NewsPick의 refresh token rotation 구조를 *이해하면서 다시* 구현.
> **모드:** 🔵 B (refresh token / reuse detection) + 🟢 A (회원 관리)

### 🔐 Refresh Token Rotation 🔵
- [x] **[MUST]** `docs/auth-design.md` — Access/Refresh 흐름 설계 문서 *먼저* 작성
- [x] **[MUST]** `refresh_tokens` 테이블 설계 (id, user_id, **token_hash**, expires_at, revoked_at, created_at + forensics: user_agent, last_used_ip)
- [x] **[MUST]** `V2__add_refresh_tokens.sql` 작성
- [x] **[MUST]** `refresh_tokens.token_hash` unique index
- [x] **[MUST]** `refresh_tokens.user_id` index (FK 부수효과로 자동 생성)
- [x] **[MUST]** `RefreshToken` 엔티티 + Repository (atomic UPDATE `revokeIfActive`, mass logout `revokeAllByUserId`)
- [x] **[MUST]** Refresh token raw 값은 DB 저장 X — **SHA-256 해시만** 저장 (CHAR(64))
- [x] **[MUST]** Access token 수명 30분~1시간으로 단축, Refresh 14일
- [x] **[MUST]** JWT claim에 `jti(UUID)` 추가
- [x] **[MUST]** Access는 `type=access`, Refresh는 `type=refresh` claim 추가
- [x] **[MUST]** `POST /api/auth/refresh` 엔드포인트
- [x] **[MUST]** Refresh token rotation 로직 (사용 시 즉시 폐기 + 새 토큰 발급)
- [x] **[MUST]** `POST /api/auth/logout` — refresh token 폐기

### 🛡️ Reuse Detection (면접 차별화 포인트) 🔵
- [x] **[MUST]** 폐기된 토큰 재사용 시 **해당 사용자 모든 세션 무효화** (mass logout)
- [ ] **[SHOULD]** 비밀번호 변경 시 모든 refresh token 폐기 (비번 변경 API 추가 시 처리)
- [x] **[SHOULD]** Refresh token 동시성 처리 — atomic UPDATE (CAS) 적용 + `@Modifying(flushAutomatically, clearAutomatically)`

### 🍪 Refresh Token 전달 방식 결정
- [x] **[MUST]** Refresh token = **httpOnly cookie**로 결정 (XSS 노출 차단)
- [x] **[MUST]** Cookie path `/auth`로 제한 (Controller 매핑 따라 — `/auth/refresh`, `/auth/logout`만 첨부)
- [x] **[MUST]** prod: `Secure=true` + `SameSite=Strict` (`auth.cookie.*` yml 키 + `@Value` 주입으로 환경별 분기)
- [x] **[MUST]** dev: `SameSite=Lax` + `Secure=false`
- [x] **[SHOULD]** Access token은 body로 반환, 클라이언트가 메모리/localStorage 저장

### 👤 회원 관리 보강 🟢
- [x] **[MUST]** `GET /users/me`
- [x] **[MUST]** `PATCH /users/me` — 닉네임 변경
- [x] **[MUST]** `PATCH /users/me/password` — 비밀번호 변경 + mass logout 자동
- [x] **[SHOULD]** `DELETE /users/me` — 회원 탈퇴 (소프트 삭제: `deletedAt` + mass logout + 재로그인 차단)

### 🛡️ 보안/유틸 정리 🔵
- [x] **[MUST]** `CustomUserDetails` 도입 (`CurrentUser.get()` 직접 캐스팅 제거 + JwtAuthFilter DB 조회 제거)
- [x] **[MUST]** `@AuthenticationPrincipal CustomUserDetails` 패턴으로 UserController 정리 (기존 컨트롤러는 `CurrentUser.getId()` 호환)
- [x] **[MUST]** 페이지네이션 안전장치 (`PageableUtils.safe`: `page<0→0`, `size 1~100`)
- [x] **[보너스]** JwtAuthFilter `type=access` 검증 — refresh 토큰으로 일반 API 호출 차단

### 🧹 운영 보조
- [ ] **[STRETCH]** 만료/폐기 refresh token cleanup 스케줄러

### 🧪 테스트
- [x] **[MUST]** AuthService — refresh rotation 성공
- [x] **[MUST]** AuthService — 만료된 refresh 거부 (`@TestPropertySource`로 expiration=1ms override + Thread.sleep — Clock 주입 없이 깔끔)
- [x] **[MUST]** AuthService — **reuse detection** (폐기된 토큰 재사용 시 mass logout)
- [x] **[보너스]** AuthService — access token으로 /refresh 시도 거부 (type 검증)
- [x] **[보너스]** AuthService — logout 후 refresh 사용 불가
- [x] **[SHOULD]** AuthService — 비밀번호 변경 시 기존 refresh 무효화 (UserService.changePassword에서 mass logout)
- [x] **[MUST]** DeckService — 남의 덱 접근 시 403 (기존 테스트)

### 📓 학습 노트
- [ ] **[SHOULD]** week-2 ~ week-5 학습 노트
- [ ] **[SHOULD]** **닫고 다시 짜기 #1** (월말): JWT 로그인 흐름 빈 파일에서 90분 안에 재구현

### ✅ Phase 1 완료 기준
- [x] 회원가입/로그인/refresh/logout — 자동화 테스트 통과 (수동 시연은 Phase 2 진입 전 한 번)
- [x] refresh rotation + reuse detection 테스트 통과 (30/30)
- [x] 비밀번호 변경 후 기존 refresh 사용 불가 (`UserService.changePassword` + mass logout)
- [x] 회원 탈퇴 흐름 통과 (deletedAt + mass logout + 재로그인 차단)
- [ ] 인증 흐름을 그림으로 설명 가능 (수동 작업 — 면접 준비 단계에서)
- [x] `docs/auth-design.md` 작성 완료 (다이어그램 추가는 Phase 8에서)
- [ ] 면접 질문 5개 답변 가능 (week-N 학습 노트에 점진적 작성 중)

### 🆕 추가 아이디어
*(공란)*

---

## 🟢 Phase 2 — CRUD 강화 + 학습 모드 (4주)

> **목표:** "혼자 쓰는 단어장 앱" 완성 + Flashcard/Quiz/Typing/오답노트 흐름 정리.
> 반복 복습은 Phase 3에서 처리.
> **모드:** 🟢 A (대부분) + 🔵 B (Typing 채점 정책)

### 📚 Card 필드 확장 🟢
- [x] **[SHOULD]** Card에 `example_sentence`, `memo`, `position` 컬럼 추가
- [x] **[SHOULD]** `V4__extend_cards.sql` (V3는 users.deleted_at에 사용됨)
- [x] **[SHOULD]** `position` 기반 정렬 API — Card 검색의 `?sort=position` 옵션
- [ ] **[STRETCH]** 드래그 순서 변경 API

### 🔍 Card 검색/정렬 🟢
- [x] **[MUST]** `GET /decks/{deckId}/cards?keyword=&starred=` (front/back LIKE 검색, 대소문자 무시, 한국어 OK)
- [x] **[MUST]** 별표 카드만 필터 (기존 + search 메서드에 통합)
- [x] **[MUST]** 정렬 옵션 (생성일/위치/별표) — `?sort=createdAt|position|starred`, position은 NULL last

### 📥 일괄 등록 강화 🟢
- [x] **[MUST]** 실패 라인 미리보기 응답 (`preview()` — failed 목록 + count 반환, 기존 구현)
- [x] **[MUST]** 최대 1000줄 제한 (ADR-020 — 초과 시 전체 거부 `BadRequestException`, 테스트 포함)
- [x] **[SHOULD]** 구분자 자동 감지 (탭/파이프/콜론/콤마/하이픈 — ADR-022, "정확히 2조각" 검증 + 테스트)
- [x] **[SHOULD]** 중복 카드 처리 — Skip (front 기준, ADR-023). 응답에 skipped 카운트 + 테스트
- [ ] **[STRETCH]** CSV 파일 업로드 (`multipart/form-data`)

### 🎯 Quiz 모드 강화 🔵
- [x] **[MUST]** 퀴즈 세션 단위 관리 (`quiz_sessions`, `quiz_questions`) — `QuizSession`/`QuizQuestion` + `startSession` Eager
- [x] **[MUST]** `V5__add_quiz_sessions.sql` (ADR-024: Eager 생성, quiz_questions.choices = JSON)
- [x] **[MUST]** 선택지 중복 제거 로직 (`buildChoices`의 `Set<String> seen`)
- [x] **[MUST]** 카드 수 5개 미만일 때 2~4지선다로 fallback (`choiceCount = Math.min(MAX_CHOICES, pool.size())`)
- [x] **[MUST]** 정답 비교 정규화 (공백/대소문자) (`trim()` + `equalsIgnoreCase()`)
- [x] **[SHOULD]** 같은 세션 내 문제 중복 방지 (shuffle 후 `subList` N장 — 같은 카드 재출제 X)
- [x] **[SHOULD]** 세션 요약 API — `GET /decks/{deckId}/quiz-sessions/{sessionId}/summary` (덱 하위 경로로 구현)

### ⌨️ Typing 모드 🔵
- [x] **[MUST]** `POST /api/decks/{deckId}/typing-sessions` (Eager 패턴, ADR-026)
- [x] **[MUST]** 사용자 입력 vs 정답 채점 (trim + equalsIgnoreCase)
- [x] **[MUST]** 쉼표로 구분된 복수 정답 허용 (`사과, 능금` → split + 각 trim)
- [x] **[MUST]** 채점 정책 문서화 (`docs/typing-policy.md`)
- [ ] **[STRETCH]** 오타 1~2개 허용 (Levenshtein) — 정확 암기 학습 가치 우선, 미도입

### 📖 Flashcard 모드 정리 🟢
- [x] **[SHOULD]** 기존 StudyService 흐름을 Flashcard 모드로 명확히 분리 (ADR-027: javadoc + `docs/learning-modes.md`)
- [x] **[SHOULD]** 학습 방향 (front→back / back→front) 옵션 — `StartStudyRequest.direction`
- [x] **[SHOULD]** 세션 결과 요약 API — `GET /study/sessions/{id}/summary`

### 🗒️ 오답노트 🟢
- [x] **[SHOULD]** 최근 틀린 카드 조회 API (ADR-028: `GET /decks/{deckId}/wrong-notes?days=30`, Aggregator 패턴)
- [x] **[SHOULD]** 오답만 모아서 재퀴즈/재타이핑 (Quiz `wrongOnly` 기존 + Typing `wrongOnly` 추가)

### 🧪 테스트
- [x] **[MUST]** Quiz — 카드 부족 시 fallback 검증 (`QuizSessionServiceTest.startSession_fallback_lessThan5Cards`)
- [x] **[MUST]** Quiz — 선택지 중복 없음 (`QuizSessionServiceTest.startSession_choicesNoDuplicate`)
- [x] **[MUST]** Typing — 채점 정책 케이스 (`TypingServiceTest` normalize/multipleAnswers/emptyInput 등)
- [x] **[SHOULD]** Import — 중복 정책 (`ImportServiceTest.importCards_skipDuplicate`)
- [x] **[SHOULD]** Card — 검색/정렬 (`CardServiceTest.카드 목록 - keyword로 front/back 검색 + 전체 조회`)

### 📓 학습 노트
- [ ] **[SHOULD]** week-6 ~ week-9 학습 노트
- [ ] **[SHOULD]** **닫고 다시 짜기 #2**: Typing 채점 로직 90분 재구현

### ✅ Phase 2 완료 기준
- [x] 모든 MUST 항목 완료 (Card 검색/정렬 · 일괄등록 · Quiz 세션 · Typing 전부 구현 + 테스트 그린)
- [x] Flashcard / Quiz / Typing 3가지 모드 **API end-to-end 검증 완료** (2026-05-29 — 회원가입→덱→카드5장→3모드. Quiz 서버판정+마스킹 / Typing trim / Flashcard known집계 전부 확인)
- [x] 퀴즈 정답 조작 방지 로직 설명 가능 (2026-05-29 구두 통과 — "프론트 판정은 조작 가능, 서버만 신뢰" + 3중 방어 정확히 답)
- [x] 면접 질문 3개 답변 가능 (week-3 노트 Q1~Q8 + 오늘 정답판정 설명 — 충분)

### 🆕 추가 아이디어

**📐 ContentItem 리팩토링 (ADR-018, Phase 2 마지막 작업)** — *큰 작업, ~2주*
- [ ] **[MUST]** V5 마이그레이션: `content_items` 부모 + `word_cards` 자식 테이블 (Card → WordCard 이전)
- [ ] **[MUST]** `ContentItem` 추상 부모 + `WordCard` 자식 (JPA `@Inheritance(JOINED)`)
- [ ] **[MUST]** `Deck.cards` → `Deck.items` (`List<ContentItem>`) 일반화
- [ ] **[MUST]** CardRepository/CardService → ContentItem 또는 WordCard로 rename
- [ ] **[MUST]** Quiz/Study 서비스가 ContentItem 기반으로 일반화 (처음엔 WordCard만 동작)
- [ ] **[MUST]** 기존 31+ 테스트 모두 통과 유지
- [ ] **[SHOULD]** `docs/migration-rule.md`에 *큰 리팩토링 패턴* 기록

---

## 🔵 Phase 3 — 반복 학습 알고리즘 (4주)

> **목표:** VocaMaster의 핵심 차별점. Leitner Box로 망각곡선 구현.
> **모드:** 🔵 B (전부 — 면접 메인 무기)

### 📊 Card Progress 모델
- [x] **[MUST]** `card_progress` 테이블 설계 (user_id, card_id, box_level, next_review_at, correct_streak, wrong_count, last_reviewed_at, version)
- [x] **[MUST]** `V7__add_card_progress.sql` (V5/V6은 quiz/typing 세션에 사용됨)
- [x] **[MUST]** `(user_id, card_id)` unique 제약
- [x] **[MUST]** `(user_id, next_review_at)` 복합 인덱스 (due 카드 조회용)
- [x] **[MUST]** `CardProgress` 엔티티 + Repository
- [x] **[MUST]** 사용자가 카드를 처음 만나면 자동 생성 (box=1, next_review_at=now) — `recordAnswer`의 `orElseGet(newProgress)`

### 🧠 Leitner Box 로직
- [x] **[MUST]** 박스별 간격 정의 (`docs/review-algorithm.md`) — 2026-07-22 작성 (간격표 + due 정의 포함)
  - box 1: 10분 / box 2: 1일 / box 3: 3일 / box 4: 7일 / box 5: 14일 / box 6: 30일
- [x] **[MUST]** `ReviewService.recordAnswer(cardId, correct)` — 박스 증감 + nextReviewAt 계산 (2026-07-22)
- [x] **[MUST]** 맞힘: box+1, 간격 증가 (`Math.min` 천장 6)
- [x] **[MUST]** 틀림: box=1로 리셋, 짧은 간격 (풀 리셋 — ADR-029)
- [x] **[MUST]** `@Version` 낙관적 락 적용 (엔티티에 적용됨 — 충돌 시 409/재시도 처리는 아래 SHOULD)
- [x] **[SHOULD]** OptimisticLockException 발생 시 **409 응답**으로 결정 (재시도는 더블클릭 중복 반영 위험으로 배제 — 2026-07-22)
- [ ] **[STRETCH]** `Clock` 주입 (시간 의존 테스트 안정화)

### 🌐 API
- [x] **[MUST]** `GET /reviews/due?deckId=` — 복습 대상 카드 목록 (JPQL `join fetch`로 N+1 방지, deckId 없으면 전체 덱 — 2026-07-22)
- [x] **[MUST]** `POST /reviews/cards/{cardId}/answer` — 정답/오답 기록 (자기평가, `@NotNull Boolean` + `@Valid`로 빈 JSON 차단)
- [x] **[SHOULD]** `GET /reviews/today-summary` — 숫자 4개: dueCount(남은 숙제) / reviewedTodayCount(오늘 복습한 **장수** — "due 완료 수" 아님) / studyCount(전 모드 답변 **횟수**) / streak(A 정책: 오늘 전엔 어제 값 유지, 어제도 없으면 0). Review 시간 계산 전체 KST 통일 (2026-07-31)

### 🔥 연속 학습일 (Streak)
- [x] **[SHOULD]** `daily_user_stats` 테이블 — "하루 한 줄 출석부", `UNIQUE(user_id, stat_date)` (2026-07-29)
- [x] **[SHOULD]** `V8__add_daily_stats.sql` *(V6은 typing 세션에 이미 사용됨 — 번호 갱신 2026-07-22)*
- [x] **[SHOULD]** 학습 기록 시 오늘 날짜 stat 업데이트 — **5개 학습 지점 전부 배선** (Review/Study/Typing/Quiz구형/Quiz세션 — "뭘 하든 공부면 출석" 사용자 결정). studyCount는 원자적 UPDATE로 증가 (lost update 방지, @Version 대신 — 통계 충돌로 본 답변까지 409 되는 것 회피). **Codex 검산 반영 2건**: 첫 학습 동시 생성은 MySQL upsert(`ON DUPLICATE KEY UPDATE`)로 500 구멍 제거 / Study·구형 Quiz 답변 메서드에 빠져 있던 `@Transactional` 추가 (답변+출석 원자성)
- [x] **[SHOULD]** streak 계산 로직 — 오늘 첫 학습 때 어제 행 보고 +1 or 1. KST 명시(`ZoneId.of("Asia/Seoul")`) — 배포 서버가 UTC여도 동일 (Codex 리뷰 반영)

### 🧪 테스트
- [x] **[MUST]** 처음 카드 → progress 생성 확인 (flush/clear 후 DB 재조회로 save 증발까지 검증)
- [x] **[MUST]** 맞힘 → box_level 증가, nextReviewAt이 *새 박스 간격* 범위(±호출시각+3일)인지 검증
- [x] **[MUST]** 틀림 → box_level=1, nextReviewAt 10분 범위 검증 (+ box 6 천장 / IDOR 403 / 404 보너스 테스트)
- [x] **[MUST]** due cards만 조회되는지 (새 카드/미래 카드 제외 + 오래 기다린 순 정렬까지)
- [x] **[MUST]** 다른 사용자 progress와 분리되는지 (+ 남의 덱 필터 403 보너스)
- [x] **[MUST]** 동시 답변 시 OptimisticLock 동작 확인 (`ReviewServiceConcurrencyTest` — 트랜잭션 인터리브로 결정적 재현, 2026-07-22)
- [x] **[SHOULD]** streak — 연속/비연속 케이스 (`StatsServiceTest` 4종: 최초/연속/끊김/같은날 + `ReviewServiceTest` 배선 확인)

### 📓 학습 노트
- [ ] **[SHOULD]** week-10 ~ week-13 학습 노트
- [x] **[SHOULD]** **닫고 다시 짜기 #3**: Leitner 박스 증감 로직 재구현 — 2026-07-26 1회차 완주 (`drill/LeitnerDrill.java`, 실행 2·3·1 확인). **정직 기록**: 구조·분기 골격·증가 방향은 자력 / 후반(타입·배열·천장·리셋·날짜 위치)은 참조 후 적용. **2회차(참조 없이) 다음 주말 재도전 예약**
- [x] **[MUST]** `docs/review-algorithm.md` — 면접 답변용 정리 (왜 Leitner? SM-2/FSRS와 차이?) — 2026-07-22 작성 완료 (30초 버전 + 면접 질문 5 포함). *체크 누락을 Codex가 발견, 2026-07-31 정정*

### ✅ Phase 3 완료 기준
- [x] 모든 MUST 항목 완료 (2026-07-31 — 문서 체크 누락분까지 정정)
- [x] 데모: 카드 답변 → 박스 변화 → 다음 복습 시점 변화 시연 가능 (2026-07-22 Swagger/curl 실연 — 2→3→1 + due 등장까지 확인)
- [x] 동시성 시나리오 1개 설명 가능 (2026-07-31 구두 통과 — "동시 답변 시 먼저 커밋 성공한 쪽이 이기고 낡은 버전은 409")
- [x] due 쿼리 인덱스 설명 가능 (2026-08-02 구두 통과 — "날짜 먼저면 유저가 쏟아지고 id 먼저면 그다음이 한정된다" 자력 재구성 + 등호 앞/범위 뒤, 카디널리티)
- [x] 면접 질문 5개 답변 가능 (2026-08-02 구두 통과 — 5개 전부 자기 말로. 특히 ⑤ "자기평가라 속여봤자 의미 없음, 소유권만 방어" 답변 우수)

### 🆕 추가 아이디어

- [ ] **[SHOULD]** MockMvc로 409 응답 계약 테스트 — 서비스 레벨 충돌 테스트는 있지만 "예외 → HTTP 409 변환"까지 고정하는 테스트는 없음 (Codex 리뷰 제안, 2026-07-22)

**📖 추가 콘텐츠 타입 (ADR-018, 점진 도입)**
- [ ] **[SHOULD]** `PassageItem` 추가 — 독해 지문 + 객관식 답 (V_N 마이그레이션 + 자식 엔티티)
- [ ] **[STRETCH]** `GrammarItem` — 문법 설명 + 예문
- [ ] **[STRETCH]** `FillBlankItem` — 빈칸 채우기
- [ ] **[MUST]** Leitner Box 알고리즘은 *모든 ContentItem*에 동일 적용 (type 무관)
- [ ] **[MUST]** PassageItem 추가 시 학습 흐름 / 퀴즈 흐름 분기 처리 (단어 = 단답 / 지문 = 다지선다 등)

---

## 🟢 Phase 4 — 공개 단어장 / 공유 (4주)

> **목표:** Quizlet 대체 느낌. 다른 사람이 쓸 수 있는 서비스.
> **모드:** 🟢 A (대부분) + 🔵 B (복사 시 데이터 일관성)

### 🔓 Visibility
- [x] **[MUST]** Deck에 `visibility` 컬럼 추가 (`PRIVATE`/`PUBLIC`/`UNLISTED`) — `@Enumerated(STRING)`, ADR-030
- [x] **[MUST]** `V9__add_deck_visibility.sql` *(계획 당시 V7 예약 → Phase 3가 V7·V8을 씀. 번호 정정)*
- [x] **[MUST]** `PATCH /api/decks/{deckId}/visibility` — 전용 엔드포인트(일반 수정과 분리) + 서비스 테스트 4개

### 🔎 공개 단어장 검색 🟢
- [x] **[MUST]** `GET /public/decks?keyword=&page=&size=` — 제목/설명 LIKE (JPQL 괄호 명시 — And/Or 누출 방지, size 100 캡)
- [x] **[MUST]** `GET /public/decks/{deckId}` — PUBLIC/UNLISTED 조회 (UNLISTED = 검색 비노출·링크 접근, 비밀링크 보안 아님)
- [x] **[MUST]** 비공개 덱 접근 시 **404** 처리 (403 X — 존재 노출 방지. 메시지까지 동일, HTTP 테스트로 박제)

### 📎 단어장 복사 🔵
- [x] **[MUST]** `POST /decks/{deckId}/copy` — 공개/UNLISTED 덱을 내 덱으로. 자기 덱은 PRIVATE이어도 가능, 남의 PRIVATE은 404 (ADR-031)
- [x] **[MUST]** `V10__add_deck_copy_tracking.sql` *(체크리스트에 누락돼 있던 마이그레이션 — 구현 시 발견·신설)*
- [x] **[MUST]** 복사본은 PRIVATE으로 생성
- [x] **[MUST]** 카드 전체 복제 (position 유지, starred·CardProgress는 리셋 — 콘텐츠/학습상태 구분)
- [x] **[MUST]** 원본 `copy_count` 증가 — DB **원자적 update** (자기 복사는 카운트 제외 — 인기 조작 방지). ⚠️ FK(original_deck_id) S락 + 카운트 X락 조합의 **실제 데드락을 동시성 테스트가 잡음** → 잠금 획득 순서 통일(카운트 먼저)로 해결
- [x] **[MUST]** `original_deck_id` 추적 (자기참조 FK, ON DELETE SET NULL)
- [x] **[SHOULD]** 카드 0개 덱 복사 → **허용** (빈 덱도 정상 상태, 테스트 포함)
- [x] **[SHOULD]** 복사 중 원본 비공개 전환 → **첫 visibility 읽기 시점 기준** (REPEATABLE READ 일관 읽기 스냅샷, ADR-031 문서화)

### ❤️ 좋아요
- [x] **[MUST]** `deck_likes` 테이블 — 복합 unique = 멱등성의 물리적 보증 (ADR-032)
- [x] **[MUST]** `V11__add_deck_likes.sql` (+ decks.like_count)
- [x] **[MUST]** `POST /public/decks/{deckId}/like` — 멱등 (레이스 시 unique 위반 → 전체 롤백 → 컨트롤러가 현재 상태 응답). ⚠️ permitAll을 `GET /public/**`로 축소 (쓰기는 인증)
- [x] **[MUST]** `DELETE /public/decks/{deckId}/like` — 지운 행 수>0일 때만 감소 (자연 멱등, 음수 불가)
- [x] **[MUST]** Deck.like_count 동기화 — 원자적 update + **X락 먼저** (복사 데드락 교훈 재적용, 동시성 회귀 테스트)
- [x] **[MUST]** `V12__deck_likes_cascade.sql` — 좋아요 달린 덱 삭제 FK 500 수리 (Codex 검산 발견). deck FK만 CASCADE, user FK는 RESTRICT(드리프트 방지) + 삭제 회귀·동일유저 더블탭 테스트
- [ ] **[STRETCH]** like_count와 deck_likes 실제 개수 불일치 복구 스케줄러

### 📈 인기/최신 정렬
- [x] **[SHOULD]** `GET /public/decks?sort=popular` — **like×5 + copy×3, 동점 최신순** (study 항 제외 — ADR-033: 현 구조에선 남들이 복사본으로 학습해서 '원작자 자기 활동' 수치가 됨 + 무한 조작 통로. Phase 6 이벤트에서 원본 귀속과 함께 재도입)
- [x] **[SHOULD]** `GET /public/decks?sort=recent` — 기본값. 그 외 sort 값은 400. 응답에 likeCount/copyCount 노출(순위 근거)

### 🏷️ 태그
- [ ] **[STRETCH]** `deck_tags` 테이블 (deck_id, tag_name)
- [ ] **[STRETCH]** `V16__add_deck_tags.sql` *(번호 재정정 — V14 읽기, V15 CASCADE가 사용, 2026-08-23)*
- [ ] **[STRETCH]** 덱 생성/수정 시 태그 등록
- [ ] **[STRETCH]** `GET /api/public/decks?tag=toeic`

### 🧪 테스트
- [x] **[MUST]** 복사 — 카드 개수 일치 / owner 변경 / copy_count 증가 (+동시 복사 2건 lost update 없음 검증)
- [x] **[MUST]** 비공개 덱 복사 시 404 (자기 덱은 예외 — 카운트 불변 검증 포함)
- [x] **[MUST]** 좋아요 중복 시 idempotent (3연타 후 카운트 1 + 취소 멱등 + 동시 좋아요 2건 정확)
- [x] **[SHOULD]** 자기 덱 좋아요 → **허용** (타 서비스 관례 + unique 제약이 1회 상한이라 조작 캡, 테스트 포함)

### 📓 학습 노트
- [ ] **[SHOULD]** week-14 ~ week-17 학습 노트
- [ ] **[SHOULD]** **닫고 다시 짜기 #4**: 단어장 복사 로직 90분 재구현

### ✅ Phase 4 완료 기준 — 전부 충족 (2026-08-11 졸업 🎓)
- [x] 모든 MUST 항목 완료 (+ SHOULD: 정렬 2, 복사 정책 2, 자기 좋아요 1)
- [x] 데모: 실서버 HTTP로 시연 (2026-08-11) — 계정 2개 → 무토큰 검색 → 복사(카드 복제·PRIVATE) → 좋아요 → **popular 1위 반영** → recent → sort=hot 400 → 무토큰 좋아요 403
- [x] 권한 정책 설명 가능 (구두 통과 — UNLISTED 꼬리질문 멘트는 ADR-030)
- [x] 복사 동시성 처리 설명 가능 (구두 통과 — lost update + FK 데드락·잠금 순서까지)
- [x] 면접 질문 3개 답변 가능 (2026-08-11 구두 3/3 통과)

### 🆕 추가 아이디어
- [ ] MockMvc 컨트롤러 슬라이스 테스트 도입 — 400 계약(빈 visibility / 오타 enum) 자동 검증. 지금은 Jackson+`@Valid` 프레임워크 보증에 의존 (프로젝트에 MockMvc 전례 없음 — 도입 자체가 별도 결정)
  - 도입 시 함께: 동일 유저 더블탭의 UNIQUE 충돌 → 실제 컨트롤러 catch → currentState 복구 경로 **결정적** 검증 (현 테스트는 최종 상태 멱등만 보증, 충돌 경로 강제는 아님 — Codex 검산 2026-08-11)
- [ ] **React 후속 백로그 (2026-08-14, Codex 재검산 — 8/19 ②③④ 완료·likedByMe/mine/공개 카드 API/상세 화면/UNLISTED 비로그인 열람/무효토큰 401)**: ① Explore 로딩 상태·페이지네이션(현재 30개 상한) ⓪ 목록의 cardCount는 여전히 덱마다 count 1번(N+1 잔존 — likedByMe만 IN으로 해결됨, 다음 정리 후보) ⑤ ~~Gradle-frontend 연결~~ 완료 8/23 (frontendBuild → processResources, bat errorlevel 중단) ⑥ ~~통계 화면용 API~~ 완료 8/23 ⑦ OSIV 명시 off + 스프링 기본 계정 소음 정리 ⑧ bat이 서버 준비 후 브라우저 열기 ⑨ ~~Card에 읽기(reading) 필드~~ **완료 8/23 (V14)** — 생성·수정·복사·3칸 가져오기(단어|읽기|뜻)·학습·퀴즈·타이핑·목록 표시, 🔊는 읽기 우선. 채점엔 미사용(옵션 후보) ⑩ ~~목업 옵션들~~ 설정 화면으로 완료 8/23 ⑫ ~~퀴즈 선택지 수 옵션~~ 완료 8/25 ⑬ **연속 재생 플레이어**(덱 단어를 N회·M초 간격·속도 조절로 쭉 읽어주기 — 순수 프론트 반나절, TTS 재활용, 오디오 파일 불필요) ⑭ ~~듣기 받아쓰기~~ **구현 완료 8/28** (/listening/:deckId — TTS 3회 onend 체인(speakTimes), 스펠링=단어·읽기 양쪽 정답, 뜻=쉼표 후보, v1 연습 모드. 후속: 세션 기록 저장) ⑮ **독해 도메인 — 11월 이후 ㉓(AI 생성)와 결합 확정** (AI가 덱 수준 맞춤 지문+4지선다 생성 → 콘텐츠 병목 해소 + LLM 통합 이력서 줄. 단독 구현은 빈 기능 위험이라 기각, 8/28) ⑪ ~~구형 Mustache 전체~~ **청소 완료 8/28** — 템플릿 4장·PageController·QuizController(구형 4 API)·WrongNote 패키지·구형 dto 5종·mustache 의존성 제거. **부수 수리: 덱 통계 퀴즈 항을 attempts→세션(quiz_questions) 기반 전환** (React 퀴즈가 통계에 안 잡히던 잠복 버그). 테스트 175→166(구형 9개 순장) ⑯ DeckDetail 별표 연타 가드(카드 id별 pending — 더블클릭 시 toggle 2번 경합, Codex 8/26) ⑰ Quiz 자동 넘김 타이머 unmount cleanup(useEffect 정리 함수 — 1초 내 화면 이탈 시 늦은 콜백) ⑱ TTS 버튼 focus-visible 링(outline:none 제거 말고 별도 링 — 키보드 접근성) ⑲ StudyService.getDeckStats 세션별 records N+1 → count/sum 집계 쿼리 전환(8/26 @Transactional 수리 때 성능은 백로그로 분리) ⑳ CI에 docker build 검증 추가 여부(현재 lint+compose config만 — 빌드 5분+ 비용 대비 검토, CD 구축 때 재결정) ㉑ 프론트 자동 테스트(숫자키·자동넘김·설정 localStorage — 백엔드 172개가 보증 못 하는 상태 흐름) ㉒ ~~다중 파일 일괄 가져오기~~ **완료 8/27** (/import-files — 파일명=덱명, BOM·전각괄호 전처리 실측, 순차 등록+결과표, 백엔드 무변경) ㉓ **AI 카드 자동 생성**(Gemini 무료 API — 단어만 치면 읽기·뜻·예문, NewsPick 요약과 같은 HTTP+프롬프트 패턴, 1~2세션, 이력서 'LLM 통합')
- [ ] 익명 요청 거부를 401로 통일 — SecurityConfig에 authenticationEntryPoint 미설정이라 현재 실측 403 (PublicDeckHttpTest가 박제). 프론트가 "로그인 필요"와 "권한 없음"을 구별하려면 401 + ErrorResponse JSON이 맞음

---

## 🔵 Phase 5 — Redis (3주)

> **목표:** "왜 Redis 썼나요?"에 한 문장으로 답할 수 있는 사용처.
> **모드:** ⚪ C (Redis 자체 학습) → 🔵 B (적용)

### 🐳 Redis 인프라 — 완료 (2026-08-12)
- [x] **[MUST]** `docker-compose.yml`에 Redis 서비스 추가 (redis:7.2-alpine, MySQL은 로컬 설치본이라 미포함)
- [x] **[MUST]** `spring-boot-starter-data-redis` 의존성 (기존 105개 테스트 그린 확인)
- [x] **[MUST]** `RedisConfig` — Lettuce 기반, 키는 평문/값은 JSON (역직렬화 타입 화이트리스트로 가젯 공격 차단)
- [x] **[MUST]** `application-dev.yml` Redis 설정 (+ **test/prod에도 짝 맞춤** — 알려진 함정 회피, timeout 300ms = fail-open 전제)
- [x] **[MUST]** `docs/redis-conventions.md` — 키 네이밍/TTL/장애 시 동작 표

#### Redis Key 컨벤션 (예시)
```
login:fail:{email}:{ip}        TTL 30분
popular:decks:{yyyyMMdd}        TTL 7일
review:summary:{userId}:{date}  TTL 5분
```

### 🚪 로그인 실패 Rate Limit — 완료 (2026-08-12, ADR-034)
- [x] **[MUST]** `LoginAttemptService` — **email 기준** 카운트 (IP 기준은 공유 IP 연쇄차단·우회 문제로 탈락, Phase 7 후보). 소문자 정규화로 대소문자 우회 차단
- [x] **[MUST]** 5분 내 5회 실패 → 30분 잠금 (고정 창, **TTL은 첫 증가에서만** — 갱신하면 창이 밀려 안 풀림)
- [x] **[MUST]** `429 Too Many Requests` + `Retry-After` 헤더. **없는 이메일도 동일하게 카운트** — 401/429 차이로 회원 명단이 새는 걸 차단 (Phase 4 존재숨김 원칙 재적용)
- [x] **[MUST]** Redis 장애 시 fail-open (통과 + 경고 로그, timeout 300ms). 닫힌 포트로 장애를 재현한 전용 테스트로 박제

### 🏆 인기 단어장 캐시 — 완료 (2026-08-12, ADR-035)
- [x] **[MUST]** Redis Sorted Set — `popular:decks` (누적 인기라 단일 키로 정정, 날짜 키는 Phase 6 급상승용으로 이월) + `ready` 표지 **TTL 시차(65/60분)**로 만료 직후 증감이 가짜 순위표 만드는 레이스 차단 (Codex 검산)
- [x] **[MUST]** 좋아요±5/복사+3/공개전환/삭제 훅 — 전부 **afterCommit** (롤백 시 실행 안 됨 → 드리프트 방지). 학습 항은 ADR-033대로 Phase 6
- [x] **[MUST]** `docs/cache-strategy.md` — 원본/사본, 무효화 표, 최종적 일관성, stale 자가치유
- [x] **[MUST]** DB fallback — Redis 예외·미가동·stale 불일치 전부 DB 경로로 (죽은 포트 재현 테스트). **캐시는 id·순서만, 권한·내용 최종 판단은 DB**
- [x] **[SHOULD]** ~~자정 재계산 스케줄러~~ → TTL 재구축(65분)이 역할 흡수 — 필요성 소멸로 미구현 (cache-strategy.md에 기록)

### ☀️ 오늘 복습 요약 캐시 — 완료 (2026-08-12, ADR-036)
- [x] **[SHOULD]** `review:summary:{userId}:{yyyyMMdd}` — cache-aside, TTL 5분. **TTL의 존재 이유 = dueCount** (행동 없이 시간만으로 변하는 값은 이벤트가 없어 TTL만이 잡음). DTO 왕복(롬복 트리오+FIELD 접근), 손상 값도 fail-open
- [x] **[SHOULD]** 학습 기록 시 무효화 — **recordStudy 단일 관문**(4개 학습 모드 전부 통과) + afterCommit. ⚠️ 기존 조기 return이면 두 번째 학습부터 무효화 누락 — 구조 수리 (Codex 검산). stats→review 의존은 Phase 6 이벤트로 분리 예정

### 🧪 테스트 (Testcontainers 도입)
- [x] **[SHOULD]** `testImplementation 'org.testcontainers:junit-jupiter'` *(Phase 3 ADR-025 때 이미 도입돼 있었음)*
- [x] **[SHOULD]** Redis Testcontainers 통합 테스트 — RedisConnectivityTest 3종 (연결+TTL / JSON 왕복 / 원자적 INCR). **JSON 왕복 테스트가 List.of 직렬화 함정을 실제로 잡음** → 컨벤션 규칙화
- [x] **[MUST]** Rate limit — 5회 실패 시 잠금 (+ 없는 이메일 동일 / 대소문자 정규화 / 성공 시 리셋 / 실제 login 6회차 429)
- [x] **[MUST]** Rate limit — 창 TTL 검증 (첫 실패에만 TTL, 두 번째 실패로 갱신되지 않음). 30분 경과 해제는 TTL 위임 — 실시간 대기 테스트는 하지 않음
- [x] **[MUST]** 인기 캐시 — 좋아요 커밋 시 점수 반영 (+재구축 정렬 / ready 없으면 증감 무시 / stale 자가치유 / 공개전환 훅)
- [x] **[MUST]** Redis 다운 시 fallback — rate limit(로그인 정상 동작)·인기 정렬(DB 폴백) 각각 닫힌 포트로 재현

### 📓 학습 노트
- [ ] **[SHOULD]** week-18 ~ week-21 학습 노트
- [x] **[MUST]** `docs/cache-strategy.md` 초안 (Phase 7에서 측정 결과 보강)

### ✅ Phase 5 완료 기준 — 전부 충족 (2026-08-12 졸업 🎓)
- [x] 모든 MUST 항목 완료 (+SHOULD 요약 캐시·Testcontainers까지)
- [x] Redis 사용처 3개 "왜" — ①rate limit: 서버가 나뉘면 HashMap은 따로 세서 못 막음 → 바깥의 공유 카운터 ②랭킹: 읽기 집중 경로를 주 DB에서 분리(인덱스 대안 알고도 선택) ③요약: 반복 집계 4방 절약(cache-aside)
- [x] Redis 다운 시뮬레이션 — 테스트 4종 상시 검증 + 구두 답("지난주 방식으로 후퇴, rate limit만 방어 포기 — 알고 선택한 트레이드오프")
- [x] 면접 질문 5개 구두 통과 (2026-08-12) — **빈칸·힌트 동반 통과** (폐쇄훈련式 스캐폴드 2단계). 백지 인출은 week note 원고화 + 소리내어 리허설로 완성할 것 — 완성 문장 세트는 세션 기록에 있음

### 🆕 추가 아이디어

**🔊 TTS — 발음 듣기 (ADR-017 개정 2026-08-23: 브라우저 내장 speechSynthesis)**
- [x] **[MUST]** `lib/tts.ts` `speak()` + 🔊 `SpeakButton` — 학습 카드(형제 배치, absolute)·덱 상세·공개 상세 단어 행. **기기 의존 브라우저 TTS** (Chrome=Google 네트워크 음성, Edge=Microsoft Natural — 실청취 "번역기보다 좋다"), 언어 자동 판별(en/ja/ko). 비용·서버·Redis 0 (2026-08-23)
- [ ] **[SHOULD]** 백로그: 한자만 있는 텍스트는 중국어여도 ja로 판별(덱 언어 필드가 정답) / 재생 실패·음성 없음 시 사용자 안내
- [ ] **[SHOULD]** 설정 화면 생기면 음성 선택 드롭다운 (기기마다 목소리가 달라서)
- [ ] **[STRETCH]** 공개 서비스 단계: 공식 Google Cloud TTS로 `speak()` 구현 교체 (무료 구간·결제 계정 — 당시 요금표 확인)
- ~~비공식 Google Translate endpoint / Redis 캐싱~~ — 철회 (약관·차단 위험, 구조 결함)

**⚛️ React 도입 (ADR-016, Phase 5 이후 시작)**
- [ ] **[MUST]** React + TypeScript + Vite 프로젝트 셋업 (NewsPick 스택과 동일)
- [ ] **[MUST]** Spring 빌드에 React 결과물 번들 (`src/main/resources/static/react/`)
- [x] **[MUST]** 핵심 화면 — 학습·덱·탐색(8/14~19) + **퀴즈(8/23: 설정→4지선다→즉시 정오→요약·오답만 다시, 숫자키 1~4)**. + **타이핑(8/23: 입력창·Enter 흐름·이번 오답 다시)**. + **통계(8/23: GET /stats/overview — 28일 활동·연속·누적·라이트너·덱별 진행률, GROUP BY 집계)**. + **설정(8/23: 테마 색·음성 선택·퀴즈 자동 넘김·덱 열 수·계정 — localStorage)**. **React 화면 전부 완료**
- [ ] **[MUST]** httpOnly Cookie + access token 자동 갱신 인터셉터
- [ ] **[MUST]** TTS 버튼 통합 (위 항목과 결합)
- [ ] **[SHOULD]** README에 "Mustache + React 공존 이유" 명시 (ADR-016 참조)

---

## 🟢 Phase 6 — 비동기 이벤트 (3주)

> **목표:** API 응답 책임과 통계/배지 책임 분리. Spring Event부터 안정화.
> **모드:** 🟢 A (Spring Event) → ⚪ C (Kafka, 선택)
> **Phase 4에서 이월 (ADR-033):** 인기 점수의 study 항 — 세션 시작 이벤트 발행 + **복사본 학습을 원본에 귀속**(original_deck_id 추적) 설계와 함께 재도입. 자기 학습 무한 반복 조작 방지 포함

### 🔔 Spring ApplicationEvent
- [x] **[MUST]** 첫 이벤트 `StudyRecordedEvent` — recordStudy가 캐시를 직접 알던 결합 해소 (ADR-037, 2026-08-19). 통과 질문: "즉시 리스너면 무슨 사고?" → 커밋 전 빈틈 재캐싱 — 자기 말로 통과
- [x] **[MUST]** 두 번째 구독자 `DeckStudyRankingListener` — study 항 재도입: 원본 귀속(평탄화)·하루 1회(DB unique 출석부)·자기 학습 제외 (ADR-038, 2026-08-22). 통과 질문 "왜 Redis SET이 아니라 DB unique" → "최종 검증은 DB, Redis 죽으면 DB 방식" 자력 통과. **부수 발견: 출석부 갭 락 데드락 잠복 버그 수리** (6명 동시 테스트가 꺼냄)
- [ ] **[SHOULD]** `DeckCopiedEvent`, `DeckLikedEvent` — 현재 직접 호출(`rankingService.onCopied/onLiked`)을 이벤트로 교체할지는 구독자가 둘 이상 생길 때 판단 (구독자 1개면 직접 호출이 더 단순 — ADR-037 원칙)
- [x] **[MUST]** `@TransactionalEventListener(phase = AFTER_COMMIT)` 사용 — 롤백 시 미호출 테스트 박제 (`rollback_doesNotEvict`)
- [x] **[MUST]** `@Async` — **혼합안 (ADR-039, 2026-08-22)**: 캐시 삭제 리스너만 비동기(유실 시 TTL 복구), 출석부·study_count는 동기(원본 기록). 통과 질문 "왜 출석부는 안 되나" → "복구 장치가 없다" 자력. `eventExecutor` 2/4/100 + CallerRunsPolicy
- [x] **[MUST]** `@EnableAsync` — `AsyncConfig`
- [x] **[MUST]** Async 리스너 예외 로깅 — `AsyncUncaughtExceptionHandler` ERROR 로그, 큐 포화 caller-runs. 둘 다 `AsyncConfigTest`로 박제
- [x] **[MUST]** 통계 집계 — 리스너가 아니라 **발행자(StatsService.recordStudy) 자체가 출석부 upsert** (원본 기록은 동기 확정 원칙). 리스너 분리 불필요로 판단
- [x] **[SHOULD]** 인기 점수 갱신 리스너 — ADR-038 (위)
- [x] **[SHOULD]** 이벤트 실패 시 재처리 정책 — ADR-039에 문서화: 캐시는 TTL, Redis 점수는 재구축이 대체 → **재처리 없음이 정책**. Outbox는 "반드시 한 번" 기능이 생길 때

### 🏅 배지/업적
- [ ] **[STRETCH]** `badges` 테이블 + `user_badges` 테이블
- [ ] **[STRETCH]** 배지 규칙 (예: 7일 streak / 100카드 학습 / 첫 공개 덱)
- [ ] **[STRETCH]** 이벤트 리스너에서 배지 부여
- [ ] **[STRETCH]** `GET /api/users/me/badges`

### 📨 Kafka 도입 (조건부)
> ⚠️ Phase 1~5의 MUST 미완료가 있으면 Kafka 도입 금지. core 안정화된 경우에만.

- [ ] **[STRETCH]** docker-compose에 Kafka + Zookeeper 추가
- [ ] **[STRETCH]** `spring-kafka` 의존성
- [ ] **[STRETCH]** Spring Event → Kafka 메시지로 교체 (1개씩 점진)
- [ ] **[STRETCH]** Consumer 멱등성 보장 (이벤트 ID 중복 처리)
- [ ] **[STRETCH]** 실패 시 재시도 정책

### 🧪 테스트
- [x] **[MUST]** 이벤트 발행 시 갱신 확인 — 커밋→캐시 삭제(비동기 폴링) / 롤백→미삭제 / 원본 귀속·하루 1회·자기 제외·6명 동시 / Redis on: 비공개 멤버 없음·커밋 후 +1·롤백 불변·공개 전환 점수 / 포화 caller-runs·예외 로그 — 총 +14 (2026-08-19~22)
- [ ] **[MUST]** Async 처리로 API 응답 시간 영향 없음 확인
- [ ] **[MUST]** AFTER_COMMIT 동작 확인 (롤백된 트랜잭션은 이벤트 발행 X)
- [ ] **[STRETCH]** Kafka Consumer 중복 메시지 idempotent

### 📓 학습 노트
- [ ] **[SHOULD]** week-22 ~ week-25 학습 노트
- [ ] **[SHOULD]** `docs/event-architecture.md`

### ✅ Phase 6 완료 기준
- [ ] 모든 MUST 항목 완료
- [ ] AFTER_COMMIT 사용 이유 설명 가능 (트랜잭션 안 한 상태에서 이벤트 처리하면 무슨 문제?)
- [ ] Async 예외 처리 전략 설명 가능
- [ ] 면접 질문 3개 답변 가능 (왜 Spring Event부터? Kafka 안 쓴 이유 / 쓴 이유?)

### 🆕 추가 아이디어

**🎮 Quest 도메인 — 게이미피케이션 (ADR-019)**
- [ ] **[SHOULD]** V_N 마이그레이션: `quests` 테이블 (시스템 정의 미션) + `user_quest_progress` 테이블 (사용자별 진행도)
  - quests: id, code (UNIQUE), title, description, target_value, reward_badge_id
  - user_quest_progress: id, user_id, quest_id, current_value, completed_at, UNIQUE(user_id, quest_id)
- [ ] **[SHOULD]** `Quest`, `UserQuestProgress` 엔티티 + Repository
- [ ] **[SHOULD]** `QuestProgressListener` — `@TransactionalEventListener(AFTER_COMMIT)` 으로 CardStudiedEvent/QuizAnsweredEvent 받아 진행도 갱신
- [ ] **[SHOULD]** 시스템 미션 INSERT 마이그레이션 (V_N+1):
  - `DAILY_20` (오늘 카드 20개)
  - `STREAK_7` (연속 7일)
  - `QUIZ_PERFECT_5` (퀴즈 5문제 연속 정답)
  - `COMPLETE_DECK` (단어장 1개 완주)
  - `SHARE_DECK` (공개 단어장 1개 만들기)
  - `COPY_5` (단어장 5개 복사)
- [ ] **[SHOULD]** `GET /quests` (시스템 미션 목록) / `GET /quests/me/progress` (내 진행도) API
- [ ] **[SHOULD]** 완료 시 배지 자동 지급 (Phase 6 배지 시스템과 결합)
- [ ] **[STRETCH]** 사용자 정의 미션 (스스로 목표 설정)

---

## 🔵 Phase 7 — 배포 / 성능 / 관측 (4주)

> **목표:** "실제로 띄워놓고 다른 사람이 쓸 수 있는" 상태.
> **모드:** ⚪ C (NewsPick 구조 학습) → 🔵 B (옮기기)

### 🔒 공개 배포 전 보안 게이트 (ADR-042)
- [x] **[MUST]** prod에서 Swagger 완전 비공개 — springdoc 비활성(엔드포인트 미등록→404), dev는 그대로 ✅ 8/25
- [x] **[MUST]** `ProdSafetyGuard` — 레포에 공개된 dev/test 시크릿 재사용·32바이트 미만이면 부팅 거부 (fail-fast) ✅ 8/25
- [x] **[MUST]** `ProdProfileTest` — prod 컨텍스트 배포 리허설을 CI에 상주 (환경변수 4종·Swagger 닫힘·Redis 없이 부팅) ✅ 8/25
- [x] **[MUST]** 의존성 현행화 — Boot 3.3.0(지원 종료) → 3.5.16, springdoc 2.8.17, jjwt 0.12.7. 사상자 1건 수리 ✅ 8/25
- [x] **[SHOULD]** OSIV off + 자동 생성 기본 계정 제외 ✅ 8/25

### 🐳 Docker
- [x] **[MUST]** `Dockerfile` 멀티스테이지 빌드 ✅ 8/25 (ADR-043) — node:20 → JDK17 → JRE17 3단(빌드 도구는 최종 이미지에 안 남음), 멀티아치 베이스(Oracle A1 ARM 대응), 비root, **기본 프로필 prod**, 565MB. 검증: 컨테이너에서 dev 시크릿 → 안전핀 부팅 거부 재현 + prod 스모크 4종(SPA 200·공개 API 200·Swagger 404·미인증 401)
- [x] **[MUST]** `docker-compose.yml`은 redis 전용 유지 (bat 일상 흐름 무파괴 — ADR-044 대안 검토로 결정 변경) ✅ 8/25
- [x] **[MUST]** `docker-compose.prod.yml` — app+MySQL+Redis, 프로젝트 분리(`vocamaster-prod`), DB·Redis 내부 전용(호스트 포트 미개방), 앱은 전용 DB 계정, 시크릿 `.env`+`.env.example` ✅ 8/25 (nginx는 ⑦에서 합류)
- [x] **[MUST]** 헬스체크 설정 — 3컨테이너 전부 + depends_on condition으로 기동 순서 제어. 리허설: 처녀 DB Flyway 15판→가입→로그인→인증 API 왕복 실측 ✅ 8/25

### 🌐 Nginx + HTTPS
- [x] **[SHOULD]** nginx 리버스 프록시 (호스트 설치, 127.0.0.1:8080 프록시) ✅ 8/26
- [x] **[SHOULD]** Let's Encrypt 인증서 — vocamaster-app.duckdns.org, 자동 갱신 타이머 active ✅ 8/26
- [x] **[SHOULD]** HTTPS 강제 리다이렉트 (301) + HSTS 1년 ✅ 8/26
- [x] **[SHOULD]** 보안 헤더 4종 — 외부 경로 실측 ✅ 8/26

### 🔄 GitHub Actions CI/CD
- [x] **[MUST]** `.github/workflows/ci.yml` — test + build ✅ 8/25. 도입 첫날 3결함 검거: ①시간대 의존 테스트(UTC 러너, -Putc 재현 스위치로 실증) ②비동기 폴링 3초 부족 ③caller-runs 테스트의 공유 실행기 간섭(전용 실행기로 격리). 5차 만에 그린 — gh CLI 인증으로 로그 직접 판독 체계 확보
- [x] **[SHOULD]** CD — ci.yml의 deploy job (needs: build → docker build → save|ssh|load → compose 동봉 → up → 헬스 게이트, docs-only 푸시는 생략) ✅ 8/27
- [x] **[MUST]** Secrets — ORACLE_HOST·ORACLE_SSH_KEY (gh secret set으로 채팅 무기록 등록) ✅ 8/27

### 📊 관측
- [ ] **[SHOULD]** `spring-boot-starter-actuator` + 필수 엔드포인트만 노출
- [ ] **[SHOULD]** 로그 설정 (`logback-spring.xml`) — 환경별 레벨
- [ ] **[STRETCH]** 요청 로깅 필터 (UserId/RequestId)

### 🚀 k6 부하 테스트
- [x] **[MUST]** k6 설치 + `tools/k6/` 디렉토리 ✅ 8/27 (계획의 tests/ 대신 tools/)
- [x] **[MUST]** 시나리오 — **범위 축소 결정(8/28)**: 4종 계획을 public deck list 1종으로. Redis 전후 비교라는 목적에 필요한 최소이자, 무료 1GB 서버에 로그인·쓰기 부하를 반복하는 위험 회피. 나머지 시나리오·seed 스크립트는 백로그
- [x] **[MUST]** **테스트 환경 스펙 기록** ✅ (A1 아닌 E2.1.Micro 2대 — performance.md 환경 절)
- [x] **[MUST]** **Redis 적용 전후 비교 측정** (p50, p95, p99) ✅ 8/28 — 1차(3.5×)는 웜/콜드 불공정으로 **철회**, 워밍업 분리+3회 반복 재측정으로 교체. 현 규모에선 DB 직접이 더 빠름 (교훈 5개는 performance.md)
- [x] **[MUST]** 결과를 `docs/performance.md`에 기록 (실제 측정값 + 원시 결과 `tools/k6/results/`) ✅
- [x] **[SHOULD]** 병목 1개 이상 찾아서 개선 사례 작성 — 코드 병목 대신 **측정 자체의 결함(콜드 JVM·closed model·워밍업 미분리)을 찾아 바로잡은 검산 사례**로 기록 ✅

### 🧪 테스트
- [ ] **[MUST]** CI에서 모든 테스트 통과
- [ ] **[SHOULD]** Testcontainers로 MySQL/Redis 통합 테스트 1개 이상

### 📓 학습 노트
- [ ] **[SHOULD]** week-26 ~ week-29 학습 노트
- [ ] **[MUST]** `docs/deployment.md` — 배포 트러블슈팅 기록

### ✅ Phase 7 완료 기준
- [ ] 모든 MUST 항목 완료
- [ ] 배포된 도메인 접속 가능 (HTTPS면 더 좋음)
- [ ] CI 그린 상태 1주 유지
- [ ] `docs/performance.md`에 실제 측정값 + 환경 스펙 기록
- [ ] 면접 질문 5개 답변 가능 (왜 멀티스테이지? p95 의미? 측정 환경 / Redis 효과 수치)

### 🆕 추가 아이디어
*(공란)*

---

## 🟢 Phase 8 — 마감 / 면접 준비 (3주)

> **목표:** 면접관이 보기 좋은 상태 + 내가 5분 안에 설명 가능한 상태.
> **모드:** 🟢 A 전부

### 📄 문서 마감
- [ ] **[MUST]** README 최종본 — 데모 URL / 핵심 기능 / 기술 스택 / 아키텍처 다이어그램 / 실행 방법
- [ ] **[MUST]** ERD 이미지 최신화
- [ ] **[MUST]** API 명세 (Swagger 캡처 또는 별도 문서)
- [ ] **[SHOULD]** 아키텍처 다이어그램 (`docs/architecture.png`)
- [ ] **[SHOULD]** 화면 캡처 / 시연 GIF (`docs/screenshots/`)
- [ ] **[SHOULD]** `docs/troubleshooting.md` — 8개월간 만난 트러블 5개 이상
- [ ] **[SHOULD]** `docs/limitations.md` — 한계와 향후 개선 계획

### 🎤 면접 대비
- [ ] **[MUST]** 핵심 질문 10개 답변 작성 (`docs/interview-qa.md`)
  - 왜 Redis를 썼는가 / Redis 장애 나면?
  - 반복 학습 알고리즘은 어떻게 설계했는가
  - 퀴즈 정답 조작을 어떻게 막았는가
  - 남의 단어장 접근을 어떻게 막았는가
  - 공개 단어장 복사는 어떻게 처리했는가
  - DB 인덱스는 어떻게 잡았는가
  - 테스트는 어떤 기준으로 작성했는가
  - 동시성 문제는 어떻게 해결했는가
  - Refresh token rotation은 왜 했는가
- [ ] **[SHOULD]** 추가 질문 20개 답변 (총 30개 목표)
- [ ] **[MUST]** 5분 발표 스크립트
- [ ] **[SHOULD]** 코드 리딩 시연 시나리오 (랜덤 파일 열어도 설명 가능)

### ✅ 최종 점검
- [ ] **[MUST]** 모든 테스트 통과
- [ ] **[MUST]** CI/CD 그린
- [ ] **[MUST]** 데모 사이트 안정 동작 1주
- [ ] **[MUST]** GitHub README의 모든 링크 동작 확인
- [ ] **[SHOULD]** 마지막 학습 노트 + 8개월 회고 (`docs/notes/retrospective.md`)
- [ ] **[SHOULD]** 핵심 도메인 테스트 충분성 점검 (Auth / Deck·Card 권한 / Import / Quiz / Review / Public Copy)
- [ ] **[STRETCH]** 코드 커버리지 측정
- [ ] **[STRETCH]** 핵심 도메인 80%+ 커버리지

### ✅ Phase 8 완료 기준 (= 프로젝트 완료 기준)
- [ ] 모든 MUST 항목 완료
- [ ] 면접관 앞에서 5분 안에 핵심 설명 가능
- [ ] 코드 임의 파일 열어도 흐름 설명 가능
- [ ] "이 프로젝트에서 가장 자랑스러운 부분 3가지" 즉답 가능
- [ ] "이 프로젝트의 한계 3가지" 즉답 가능

### 🆕 추가 아이디어
*(공란)*

---

## 📅 매월 의식 (Reminder)

- [ ] 월말 — 그 달 핵심 기능 "닫고 다시 짜기" 90분 훈련
- [ ] 월말 — `docs/notes/month-N-summary.md` 작성
- [ ] 월말 — 다음 달 Phase의 모드(A/B/C) 미리 결정
- [ ] 월말 — MUST 미완료 항목 점검 + 다음 달로 이월할지 판단

---

## 🚫 의도적으로 안 하는 것 (Out of Scope)

> 8개월 안에 욕심내면 망함. 면접관이 물으면 "범위 밖이라 빼고 핵심에 집중했다"로 답.

- ❌ MSA / 멀티 서비스 분리
- ❌ Kubernetes를 메인 배포로 (부록 문서로만 가능)
- ❌ Elasticsearch (MySQL FULLTEXT로 충분)
- ❌ 결제 / 구독
- ❌ 모바일 앱
- ❌ React 풀 SPA (Mustache 메인 + Phase 5 이후 핵심 화면 3~5개만 React, AI 작성 — ADR-016 참조)
- ❌ AI/LLM 단어 자동 생성 (NewsPick과 차별화 위해)
- ❌ 실시간 협업 편집
- ❌ 소셜 팔로우 / 피드
- ❌ 알림 시스템 풀 구현 (이메일/푸시/인앱 다 X)

---

## 📚 메모리 연동

이 체크리스트와 함께 보면 좋은 메모리:
- `~/.claude/projects/.../memory/vocamaster_roadmap.md` — 월별 의도/이유
- `~/.claude/projects/.../memory/feedback_workflow.md` — A/B 모드 협업 규칙
- `~/.claude/projects/.../memory/portfolio_strategy.md` — 두 프로젝트 포지셔닝
