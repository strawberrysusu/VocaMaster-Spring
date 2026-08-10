ALTER TABLE decks ADD COLUMN like_count BIGINT NOT NULL DEFAULT 0;

CREATE TABLE deck_likes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    deck_id BIGINT NOT NULL,
    created_at DATETIME(6),
    CONSTRAINT uq_deck_likes_user_deck UNIQUE (user_id, deck_id),
    CONSTRAINT fk_deck_likes_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_deck_likes_deck FOREIGN KEY (deck_id) REFERENCES decks(id)
);
