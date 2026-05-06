package com.vocamaster.common;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Getter
@RequiredArgsConstructor
public class ErrorResponse {
    private final int status;
    private final String code;
    private final String message;
    private final LocalDateTime timestamp;

    public static ErrorResponse of(int status, String code, String message) {
        return new ErrorResponse(status, code, message, LocalDateTime.now());
    }
}
