-- 좋아요 달린 덱 삭제 시 FK 위반 500 수리 (Codex 검산, ADR-032 보강).
-- deck FK만 CASCADE: 덱 행이 사라지므로 like_count 드리프트 없음.
-- user FK는 RESTRICT 유지: 소프트 삭제 정책 + CASCADE 시 덱들의 like_count가 조용히 어긋남.
ALTER TABLE deck_likes DROP FOREIGN KEY fk_deck_likes_deck;
ALTER TABLE deck_likes ADD CONSTRAINT fk_deck_likes_deck
    FOREIGN KEY (deck_id) REFERENCES decks(id) ON DELETE CASCADE;
