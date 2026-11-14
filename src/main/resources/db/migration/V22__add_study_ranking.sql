-- 학습 랭킹 (11/14, ADR-059) — 답한 카드 1장 = 1점, 오늘 / 전체
-- ① 닉네임 가리기: 켜면 랭킹에 'qjw***'처럼 앞 몇 글자만 나간다 (가리기는 서버가 한다 — API 응답에 진짜 닉네임이 안 실리게)
ALTER TABLE users ADD COLUMN ranking_masked BOOLEAN NOT NULL DEFAULT FALSE;

-- ② 오늘 랭킹은 '그날 줄'만 점수 순으로 본다 — 기존 (user_id, stat_date) 유니크는 날짜만으로는 못 탄다
CREATE INDEX idx_daily_stats_date_count ON daily_user_stats (stat_date, study_count);
