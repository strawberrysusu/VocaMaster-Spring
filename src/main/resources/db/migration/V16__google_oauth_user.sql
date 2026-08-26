-- 구글 로그인 (ADR-047): 소셜 가입자는 비밀번호가 없다 + 가입 경로 기록
-- 기존 행은 DEFAULT 'local'로 자동 채움 — 데이터 이관 불필요
ALTER TABLE users MODIFY password VARCHAR(255) NULL;
ALTER TABLE users ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'local';
