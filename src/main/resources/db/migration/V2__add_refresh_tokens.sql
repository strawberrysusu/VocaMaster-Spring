-- Phase 1: refresh_tokens 테이블
-- DB-stateful refresh token rotation + reuse detection을 위한 저장소
-- raw token은 저장하지 않고 SHA-256 해시만 저장

CREATE TABLE refresh_tokens (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    token_hash    CHAR(64)     NOT NULL,                                    -- SHA-256 hex 64자
    expires_at    DATETIME(6)  NOT NULL,
    revoked_at    DATETIME(6),                                              -- soft delete: NULL=살아있음, NOT NULL=폐기 시각 (reuse detection 전제)
    created_at    DATETIME(6)  NOT NULL,
    user_agent    VARCHAR(255),                                             -- forensics: 발급 기기
    last_used_ip  VARCHAR(45),                                              -- forensics: 마지막 사용 IP (IPv6 최대 45자)
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_token_hash (token_hash),                   -- 중복 차단 + 자동 인덱스 (refresh 검증 시 매번 lookup)
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) -- 무결성 + user_id 자동 인덱스 (mass logout 쿼리용)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
