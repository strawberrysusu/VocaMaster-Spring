package com.vocamaster.auth.dto;

/**
 * Service ↔ Controller 사이 전달용 내부 DTO.
 * Controller가 accessToken은 body로, refreshToken은 httpOnly cookie로 분리해 응답.
 */
public record TokenPair(String accessToken, String refreshToken) {
}
