-- V17 후속 단건 수리: 뜻에 쉼표가 있던 줄('険しい（けわしい）, 험하다, 가파르다')이
-- 쉼표 3칸으로 갈려 reading에 뜻 앞부분이 들어간 카드 1장.
-- SET 순서 주의: back을 먼저(원본 reading='험하다' 참조) → reading → front.
UPDATE cards
SET back    = CONCAT(reading, ', ', back),
    reading = 'けわしい',
    front   = '険しい'
WHERE front = '険しい（けわしい）' AND reading = '험하다';
