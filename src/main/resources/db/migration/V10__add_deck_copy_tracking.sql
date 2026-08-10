ALTER TABLE decks ADD COLUMN copy_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE decks ADD COLUMN original_deck_id BIGINT NULL;
ALTER TABLE decks ADD CONSTRAINT fk_decks_original_deck
    FOREIGN KEY (original_deck_id) REFERENCES decks(id) ON DELETE SET NULL;
