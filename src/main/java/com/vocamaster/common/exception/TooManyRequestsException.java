package com.vocamaster.common.exception;

import lombok.Getter;

/**
 * 429 Too Many Requests — 요청 자체는 유효하나 제한에 걸림 (ADR-034).
 * 401(자격 증명 실패)과 구분되는 이유: 클라이언트가 "다시 시도해도 소용없고, 언제 풀리는지"를 알아야 함.
 */
@Getter
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
