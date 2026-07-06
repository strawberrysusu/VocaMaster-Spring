-- Phase 3 #1: Leitner Box 간격 반복 (ADR-029)
-- card_progress: "이 유저가 이 카드를 얼마나 외웠나" — 유저-카드 쌍마다 1행
-- 핵심 2컬럼: box_level (1~6) + next_review_at (이 시각 지나면 due = 복습 대상)
-- 행이 없는 카드 = 아직 한 번도 복습 안 한 카드 → 첫 답변 시 서비스에서 생성 (box=1)
-- version: JPA @Version 낙관적 락 (같은 카드 동시 답변 충돌 방지)

CREATE TABLE card_progress (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    user_id          BIGINT      NOT NULL,
    card_id          BIGINT      NOT NULL,
    box_level        INT         NOT NULL,                                    -- 1~6 (Leitner 박스, 6이 천장)
    next_review_at   DATETIME(6) NOT NULL,                                    -- 복습 예정 시각 (due 판정 기준)
    correct_streak   INT         NOT NULL,                                    -- 연속 정답 수 (틀리면 0 리셋)
    wrong_count      INT         NOT NULL,                                    -- 누적 오답 수 (통계/오답노트용)
    last_reviewed_at DATETIME(6),                                             -- NULL = 생성만 되고 아직 답변 전
    version          BIGINT      NOT NULL,                                    -- JPA @Version (낙관적 락)
    PRIMARY KEY (id),
    CONSTRAINT uq_card_progress_user_card UNIQUE (user_id, card_id),          -- 같은 유저-카드 쌍 중복 방지
    CONSTRAINT fk_card_progress_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_card_progress_card FOREIGN KEY (card_id) REFERENCES cards (id),
    INDEX idx_card_progress_due (user_id, next_review_at)                     -- due 조회용: WHERE user_id=? AND next_review_at<=now
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
