-- V19: 대량 임포트(8/28)로 들어온 두 오염 형식 일괄 수리 (V17과 같은 계열, 2026-08-29)
--
-- 원인: 가져오기 전처리가 CRLF(\r) 줄끝을 못 벗겨 day02형 정규식이 전멸(A형 3,369장),
--       n1모음형(탭 2칸 — 읽기와 뜻이 한 칸)은 처리 규칙 자체가 없었음(B형 4,755장).
--       전처리는 프론트에서 수리했고(재발 방지), 이 파일은 이미 들어온 데이터를 고친다.
--
-- ⚠ COLLATE utf8mb4_bin 명시 이유: 기본 ai_ci는 전각（）과 반각()을 같은 문자로 취급한다.
--   n1모음 카드의 반각 괄호는 정당한 주석(예: "(機械を)稼働", "(기계를) 가동")이라
--   절대 건드리면 안 됨 — bin으로 전각만 정밀 매칭.

-- [A형] front가 "単語（よみ）" — 괄호 안을 reading으로, front는 괄호 앞만.
--       SET 좌→우 순차 적용(MySQL): front를 참조하는 reading을 먼저 계산해야 한다 (V17 검증 순서).
UPDATE cards
SET reading = SUBSTRING_INDEX(SUBSTRING_INDEX(front COLLATE utf8mb4_bin, '（', -1), '）', 1),
    front   = TRIM(SUBSTRING_INDEX(front COLLATE utf8mb4_bin, '（', 1))
WHERE (reading IS NULL OR reading = '')
  AND front COLLATE utf8mb4_bin REGEXP '^[^（]+（[^（）]+）$';

-- [B형] back이 "（よみ） 뜻" — 선두 전각 괄호를 reading으로(비어 있을 때만), back은 나머지.
--       가드: 첫 '）' 뒤에 실제 뜻이 남는 행만 (뜻이 통째로 괄호뿐이면 back이 비는 사고 방지)
UPDATE cards
SET reading = CASE WHEN (reading IS NULL OR reading = '')
                   THEN TRIM(SUBSTRING(SUBSTRING_INDEX(back COLLATE utf8mb4_bin, '）', 1), 2))
                   ELSE reading END,
    back    = TRIM(SUBSTRING(back, CHAR_LENGTH(SUBSTRING_INDEX(back COLLATE utf8mb4_bin, '）', 1)) + 2))
WHERE back COLLATE utf8mb4_bin LIKE '（%）%'
  AND CHAR_LENGTH(back) > CHAR_LENGTH(SUBSTRING_INDEX(back COLLATE utf8mb4_bin, '）', 1)) + 1;
