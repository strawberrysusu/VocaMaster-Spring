-- Phase 3: 연속 학습일 (Streak) — 사용자별 "하루 한 줄" 출석부
-- 한 행 = 이 유저가 이 날짜에 공부했다 + 그날 학습 횟수 + 그 시점 연속 며칠째
-- streak은 행 생성 시 어제 행을 보고 계산해 저장 (조회 O(1), 이력 보존 → 잔디 그래프 재료)
-- 모든 학습 모드(Review/Quiz/Typing/Study)가 출석으로 인정됨
-- @Version 없음 (의도적): 통계 충돌로 본 답변까지 409로 죽이지 않기 위해 study_count는 원자적 UPDATE로 증가

CREATE TABLE daily_user_stats (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    stat_date   DATE   NOT NULL,                                          -- KST 자정 기준 "그 날"
    study_count INT    NOT NULL DEFAULT 0,                                -- 그날 학습 활동 횟수 (모드 무관 합산)
    streak      INT    NOT NULL DEFAULT 1,                                -- 이 날 기준 연속 학습 일수
    PRIMARY KEY (id),
    CONSTRAINT uq_daily_stats_user_date UNIQUE (user_id, stat_date),     -- 한 사람의 하루 줄은 하나뿐
    CONSTRAINT fk_daily_stats_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
