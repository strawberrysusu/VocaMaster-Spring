-- 학습 세션 일괄 제출 (2026-08-31).
-- 세션 도중의 알아요/몰라요는 프론트의 '임시 답안'이고, '완료' 한 번으로 전체가 한 트랜잭션에 반영된다.
-- 그래야 이전 카드로 돌아가 답을 고칠 수 있다 (즉시 저장 구조에서는 되돌리려면 박스를 되돌려야 했고,
-- 오답은 box_level을 1로 풀 리셋해서 이전 값이 어디에도 안 남는다).
--
-- 이 표의 유일한 일은 "같은 제출이 두 번 진행도를 움직이지 못하게" 막는 것이다.
-- (user_id, submission_id) unique가 최종 보증 — 프론트의 더블클릭 방어나 네트워크 재시도 억제는 보증자가 못 된다.
-- 서비스는 예외가 아니라 INSERT IGNORE의 반환값 0으로 중복을 안다:
-- 같은 트랜잭션 안에서 unique 위반을 catch하면 rollback-only에 걸리기 때문 (deck_study_days V13과 같은 관용구).
--
-- user FK RESTRICT: 소프트 삭제 정책 유지 (deck_study_days V13·deck_likes V12와 동일 판단).
CREATE TABLE review_submissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    submission_id VARCHAR(36) NOT NULL,   -- CHAR(36)이면 Hibernate 스키마 검증이 VARCHAR 기대와 어긋나 부팅이 막힌다 (실측)
    answer_count INT NOT NULL,
    known_count INT NOT NULL,
    -- 정렬된 답안(cardId:correct,...)의 SHA-256. 같은 submissionId로 '다른 답'이 오면 409로 거른다:
    -- 응답만 유실된 뒤 사용자가 답을 고쳐 재전송하면, 해시가 없으면 바뀐 답이 조용히 버려진다.
    payload_hash VARCHAR(64) NOT NULL,
    created_at DATETIME(6),
    CONSTRAINT uq_review_submissions UNIQUE (user_id, submission_id),
    CONSTRAINT fk_review_submissions_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
