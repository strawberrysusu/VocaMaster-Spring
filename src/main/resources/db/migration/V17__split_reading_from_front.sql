-- 데이터 수리 (백로그 ㉒ 후속): 파일 가져오기 초기 버전이 '単語（よみ）' 형식(day02형)을
-- 통째로 front에 넣은 카드들을 front=단어 / reading=읽기로 분리한다.
-- 조건이 정확히 '본체（읽기）' 꼴 + 읽기 비어있음일 때만 — 뜻 속 반각 괄호나 기존 읽기 보유 카드는 무접촉.
-- reading을 먼저 계산(원본 front 기준)한 뒤 front를 갱신하는 SET 순서가 핵심.
UPDATE cards
SET reading = SUBSTRING_INDEX(SUBSTRING_INDEX(front, '（', -1), '）', 1),
    front   = SUBSTRING_INDEX(front, '（', 1)
WHERE (reading IS NULL OR reading = '')
  AND front REGEXP '^[^（]+（[^（）]+）$';
