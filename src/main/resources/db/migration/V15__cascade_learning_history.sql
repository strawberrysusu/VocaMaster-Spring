-- 삭제 정책 확정 (Codex 전수 감사 2026-08-23, ADR-040): 학습 이력은 카드·덱과 생사를 같이한다 (CASCADE).
-- 배경: 12개 FK가 전부 RESTRICT라 한 번이라도 학습한 카드/덱 삭제가 500. soft delete는 모든 조회에 필터가 끼어 현 규모엔 과함.
-- deck_likes(V12)·deck_study_days(V13)는 이미 CASCADE — 같은 판단의 확장.

-- 카드 → 학습 이력
ALTER TABLE quiz_attempts    DROP FOREIGN KEY fk_quiz_attempts_card;
ALTER TABLE quiz_attempts    ADD CONSTRAINT fk_quiz_attempts_card    FOREIGN KEY (card_id) REFERENCES cards(id) ON DELETE CASCADE;
ALTER TABLE study_records    DROP FOREIGN KEY fk_study_records_card;
ALTER TABLE study_records    ADD CONSTRAINT fk_study_records_card    FOREIGN KEY (card_id) REFERENCES cards(id) ON DELETE CASCADE;
ALTER TABLE quiz_questions   DROP FOREIGN KEY fk_quiz_questions_card;
ALTER TABLE quiz_questions   ADD CONSTRAINT fk_quiz_questions_card   FOREIGN KEY (card_id) REFERENCES cards(id) ON DELETE CASCADE;
ALTER TABLE typing_questions DROP FOREIGN KEY fk_typing_questions_card;
ALTER TABLE typing_questions ADD CONSTRAINT fk_typing_questions_card FOREIGN KEY (card_id) REFERENCES cards(id) ON DELETE CASCADE;
ALTER TABLE card_progress    DROP FOREIGN KEY fk_card_progress_card;
ALTER TABLE card_progress    ADD CONSTRAINT fk_card_progress_card    FOREIGN KEY (card_id) REFERENCES cards(id) ON DELETE CASCADE;

-- 덱 → 카드 (JPA cascade가 먼저 지우지만 DB도 같은 규칙으로 — 이중 방어)
ALTER TABLE cards DROP FOREIGN KEY fk_cards_deck;
ALTER TABLE cards ADD CONSTRAINT fk_cards_deck FOREIGN KEY (deck_id) REFERENCES decks(id) ON DELETE CASCADE;

-- 덱 → 세션 → 문제/기록
ALTER TABLE quiz_sessions    DROP FOREIGN KEY fk_quiz_sessions_deck;
ALTER TABLE quiz_sessions    ADD CONSTRAINT fk_quiz_sessions_deck    FOREIGN KEY (deck_id) REFERENCES decks(id) ON DELETE CASCADE;
ALTER TABLE typing_sessions  DROP FOREIGN KEY fk_typing_sessions_deck;
ALTER TABLE typing_sessions  ADD CONSTRAINT fk_typing_sessions_deck  FOREIGN KEY (deck_id) REFERENCES decks(id) ON DELETE CASCADE;
ALTER TABLE study_sessions   DROP FOREIGN KEY fk_study_sessions_deck;
ALTER TABLE study_sessions   ADD CONSTRAINT fk_study_sessions_deck   FOREIGN KEY (deck_id) REFERENCES decks(id) ON DELETE CASCADE;
ALTER TABLE quiz_questions   DROP FOREIGN KEY fk_quiz_questions_session;
ALTER TABLE quiz_questions   ADD CONSTRAINT fk_quiz_questions_session   FOREIGN KEY (session_id) REFERENCES quiz_sessions(id) ON DELETE CASCADE;
ALTER TABLE typing_questions DROP FOREIGN KEY fk_typing_questions_session;
ALTER TABLE typing_questions ADD CONSTRAINT fk_typing_questions_session FOREIGN KEY (session_id) REFERENCES typing_sessions(id) ON DELETE CASCADE;
ALTER TABLE study_records    DROP FOREIGN KEY fk_study_records_session;
ALTER TABLE study_records    ADD CONSTRAINT fk_study_records_session    FOREIGN KEY (session_id) REFERENCES study_sessions(id) ON DELETE CASCADE;
