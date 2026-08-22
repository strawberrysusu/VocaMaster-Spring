-- Phase 6: 인기 점수 study 항 재도입 (ADR-033 이월 → ADR-038).
-- 의미 = "한 사용자가 한 원본 덱에 하루 최대 1점" (누적 학습자-일수). 답변 수가 아님.
ALTER TABLE decks ADD COLUMN study_count BIGINT NOT NULL DEFAULT 0;

-- 랭킹용 출석부. (user, deck, date) unique가 "하루 1회"의 최종 보증 — Redis는 사라질 수 있어 보증자가 못 됨.
-- deck FK CASCADE: 덱이 사라지면 출석부도 의미 없음 (deck_likes V12와 동일 판단).
-- user FK RESTRICT: 소프트 삭제 정책 유지 (deck_likes와 동일).
CREATE TABLE deck_study_days (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    deck_id BIGINT NOT NULL,
    stat_date DATE NOT NULL,
    created_at DATETIME(6),
    CONSTRAINT uq_deck_study_days UNIQUE (user_id, deck_id, stat_date),
    CONSTRAINT fk_deck_study_days_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_deck_study_days_deck FOREIGN KEY (deck_id) REFERENCES decks(id) ON DELETE CASCADE
);

-- original_deck_id 평탄화: 복사본의 복사본이 '직전 부모'를 가리키던 것을 '최상위 원본'으로.
-- 이후 DeckService.copy가 복사 시점에 평탄화하므로 새 체인은 생기지 않음. 기존 데이터는 깊이 5까지 수습.
UPDATE decks d JOIN decks p ON d.original_deck_id = p.id SET d.original_deck_id = p.original_deck_id WHERE p.original_deck_id IS NOT NULL;
UPDATE decks d JOIN decks p ON d.original_deck_id = p.id SET d.original_deck_id = p.original_deck_id WHERE p.original_deck_id IS NOT NULL;
UPDATE decks d JOIN decks p ON d.original_deck_id = p.id SET d.original_deck_id = p.original_deck_id WHERE p.original_deck_id IS NOT NULL;
UPDATE decks d JOIN decks p ON d.original_deck_id = p.id SET d.original_deck_id = p.original_deck_id WHERE p.original_deck_id IS NOT NULL;
UPDATE decks d JOIN decks p ON d.original_deck_id = p.id SET d.original_deck_id = p.original_deck_id WHERE p.original_deck_id IS NOT NULL;
