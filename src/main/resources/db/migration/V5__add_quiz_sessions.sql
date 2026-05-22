-- Phase 2 #4: Quiz 세션 단위 관리 (ADR-024)
-- quiz_sessions: 1회 퀴즈 = N문제 묶음
-- quiz_questions: 세션 시작 시 N개 미리 생성 (Eager) → 일관성 + 중복 출제 방지

CREATE TABLE quiz_sessions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    deck_id     BIGINT       NOT NULL,
    direction   VARCHAR(20)  NOT NULL,                                       -- FRONT_TO_BACK / BACK_TO_FRONT
    total       INT          NOT NULL,                                       -- 세션 문제 수 (보통 10)
    started_at  DATETIME(6)  NOT NULL,
    ended_at    DATETIME(6),                                                 -- NULL = 진행 중, NOT NULL = 완료 시각
    PRIMARY KEY (id),
    CONSTRAINT fk_quiz_sessions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_quiz_sessions_deck FOREIGN KEY (deck_id) REFERENCES decks (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE quiz_questions (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    session_id      BIGINT       NOT NULL,
    card_id         BIGINT       NOT NULL,
    question_order  INT          NOT NULL,                                   -- 0~N-1 출제 순서
    question_text   VARCHAR(500) NOT NULL,                                   -- 화면에 보여줄 문제 텍스트
    choices_json    JSON         NOT NULL,                                   -- ["선택지1","선택지2",...] (ADR-024: JSON 컬럼 단순화)
    correct_answer  VARCHAR(500) NOT NULL,
    selected_answer VARCHAR(500),                                            -- NULL = 아직 안 풀음
    is_correct      BIT(1),                                                  -- NULL = 아직, 1 = 정답, 0 = 오답
    answered_at     DATETIME(6),                                             -- NULL = 아직
    PRIMARY KEY (id),
    CONSTRAINT fk_quiz_questions_session FOREIGN KEY (session_id) REFERENCES quiz_sessions (id),
    CONSTRAINT fk_quiz_questions_card    FOREIGN KEY (card_id)    REFERENCES cards (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
