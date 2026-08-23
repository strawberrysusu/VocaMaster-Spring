-- 읽기(요미가나) — 일본어 단어의 발음 표기 (会議 → かいぎ). 표시·🔊 전용, 채점·검색엔 관여 안 함 (2026-08-23).
-- nullable: 영어 덱 등 읽기가 필요 없는 카드가 대부분.
ALTER TABLE cards ADD COLUMN reading VARCHAR(200) NULL AFTER front;
