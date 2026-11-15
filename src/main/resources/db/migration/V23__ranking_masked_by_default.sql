-- 학습 랭킹 닉네임: 기본 = 가림(비공개). 공개는 설정에서 직접 체크해야 한다 (11/15 사용자 결정, ADR-059 보완)
-- V22는 기본을 '공개'로 냈었다 — 그 사이 직접 '공개'를 고른 사람은 없으니(고르는 칸이 없었다) 모두 가림으로 맞춘다.
-- 칸 이름은 그대로 둔다: 이름을 바꾸면 배포가 실패해 이전 버전으로 되돌아갈 때 그 버전이 이 칸을 못 찾는다
ALTER TABLE users ALTER COLUMN ranking_masked SET DEFAULT TRUE;
UPDATE users SET ranking_masked = TRUE;
