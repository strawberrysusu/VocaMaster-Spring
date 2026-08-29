-- 📁 폴더 — 덱 분류 (동결 전 마지막 기능, 2026-08-29. N1/N2/토플 등 컬렉션이 52+개로 늘며 실수요 발생).
--
-- FK 선택 이유:
-- - folders.user RESTRICT: 소프트 삭제 정책 유지 (deck_likes·deck_study_days와 동일 판단)
-- - decks.folder **ON DELETE SET NULL**: 폴더는 '분류'일 뿐 — 폴더를 지워도 덱은 살아서 미분류로.
--   (덱→카드의 CASCADE와 정반대 선택: 카드는 덱 없이 의미가 없지만, 덱은 폴더 없이도 완전한 실체)
CREATE TABLE folders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME(6),
    CONSTRAINT fk_folders_user FOREIGN KEY (user_id) REFERENCES users(id)
);

ALTER TABLE decks ADD COLUMN folder_id BIGINT NULL;
ALTER TABLE decks ADD CONSTRAINT fk_decks_folder
    FOREIGN KEY (folder_id) REFERENCES folders(id) ON DELETE SET NULL;
