-- Phase 2 #5: Typing 세션 단위 관리 (ADR-026)
-- typing_sessions: 1회 타이핑 = N문제 묶음 (Quiz V5와 동일 패턴)
-- typing_questions: 시작 시 N개 미리 생성 (Eager) → 일관성 + 중복 출제 방지
-- 차이 vs quiz_questions: choices_json 없음 (open-ended), typed_answer (사용자 입력)

CREATE TABLE typing_sessions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    deck_id     BIGINT       NOT NULL,
    direction   VARCHAR(20)  NOT NULL,                                       -- FRONT_TO_BACK / BACK_TO_FRONT
    total       INT          NOT NULL,                                       -- 세션 문제 수 (보통 10)
    started_at  DATETIME(6)  NOT NULL,
    ended_at    DATETIME(6),                                                 -- NULL = 진행 중, NOT NULL = 완료 시각
    PRIMARY KEY (id),
    CONSTRAINT fk_typing_sessions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_typing_sessions_deck FOREIGN KEY (deck_id) REFERENCES decks (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE typing_questions (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    session_id      BIGINT       NOT NULL,
    card_id         BIGINT       NOT NULL,
    question_order  INT          NOT NULL,                                   -- 0 ~ N-1 출제 순서
    question_text   VARCHAR(500) NOT NULL,                                   -- 화면에 보여줄 문제 텍스트
    correct_answer  VARCHAR(500) NOT NULL,                                   -- 쉼표 복수 정답 가능 (예: "사과, 능금")
    typed_answer    VARCHAR(500),                                            -- NULL = 아직 안 풀음 (사용자가 친 텍스트)
    is_correct      BIT(1),                                                  -- NULL = 아직, 1 = 정답, 0 = 오답
    answered_at     DATETIME(6),                                             -- NULL = 아직
    PRIMARY KEY (id),
    CONSTRAINT fk_typing_questions_session FOREIGN KEY (session_id) REFERENCES typing_sessions (id),
    CONSTRAINT fk_typing_questions_card    FOREIGN KEY (card_id)    REFERENCES cards (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
