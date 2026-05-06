-- Phase 0: VocaMaster 초기 스키마
-- 현재 엔티티(User, Deck, Card, QuizAttempt, StudySession, StudyRecord) 6개 매핑

CREATE TABLE users (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    email       VARCHAR(255) NOT NULL,
    password    VARCHAR(255) NOT NULL,
    nickname    VARCHAR(255) NOT NULL,
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE decks (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    title       VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    user_id     BIGINT       NOT NULL,
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_decks_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE cards (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    front       VARCHAR(255) NOT NULL,
    back        VARCHAR(255) NOT NULL,
    starred     BIT(1),
    deck_id     BIGINT       NOT NULL,
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_cards_deck FOREIGN KEY (deck_id) REFERENCES decks (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE quiz_attempts (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    deck_id         BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    card_id         BIGINT       NOT NULL,
    direction       VARCHAR(255),
    selected_answer VARCHAR(255),
    correct_answer  VARCHAR(255),
    is_correct      BIT(1),
    created_at      DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_quiz_attempts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_quiz_attempts_card FOREIGN KEY (card_id) REFERENCES cards (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE study_sessions (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    deck_id       BIGINT NOT NULL,
    user_id       BIGINT NOT NULL,
    direction     VARCHAR(255),
    starred_only  BIT(1),
    created_at    DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_study_sessions_deck FOREIGN KEY (deck_id) REFERENCES decks (id),
    CONSTRAINT fk_study_sessions_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE study_records (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    session_id  BIGINT NOT NULL,
    card_id     BIGINT NOT NULL,
    known       BIT(1),
    created_at  DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_study_records_session FOREIGN KEY (session_id) REFERENCES study_sessions (id),
    CONSTRAINT fk_study_records_card    FOREIGN KEY (card_id)    REFERENCES cards (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
