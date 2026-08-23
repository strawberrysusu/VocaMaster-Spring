package com.vocamaster.common;

import com.vocamaster.common.ErrorResponse;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.common.exception.ForbiddenException;
import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.common.exception.TooManyRequestsException;
import com.vocamaster.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // === 새 커스텀 예외 4개

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "NOT_FOUND", ex.getMessage()));
    }
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex){
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, "FORBIDDEN", ex.getMessage()));
    }
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex){
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, "UNAUTHORIZED", ex.getMessage()));
    }
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, "BAD_REQUEST", ex.getMessage()));
    }

    // 429 — Retry-After 헤더는 HTTP 표준 계약: "몇 초 뒤에 다시 오라"를 클라이언트가 기계적으로 읽음 (ADR-034)
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(ErrorResponse.of(429, "TOO_MANY_REQUESTS", ex.getMessage()));
    }

    // ===Validation / 예상 못한 예외 ===

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("유효성 검증 실패");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, "BAD_REQUEST", message));
    }

    // 동시 요청 충돌 (@Version 낙관적 락) — 낡은 버전의 쓰기 거부. 클라이언트는 재조회 후 재시도 (ADR-029)
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "CONFLICT", "다른 요청이 먼저 처리되었습니다. 최신 상태를 다시 조회해주세요"));
    }

    // 없는 정적 경로/URL — 스프링이 던지는 404성 예외가 catch-all에 걸려 500으로 둔갑하던 것 수리
    // (SPA 서빙 붙이다 발견 — 그동안 모든 미존재 경로가 "서버 내부 오류"로 보였음)
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(
            org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "NOT_FOUND", "요청한 경로를 찾을 수 없습니다"));
    }

    // 본문 자체가 안 읽힘 (깨진 JSON, 잘못된 인코딩 등) = 클라이언트 잘못 → 400.
    // 이 핸들러가 없으면 catch-all에 걸려 500 "서버 오류"로 둔갑함 (2026-07-22 시연에서 발견)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, "BAD_REQUEST", "요청 본문(JSON)을 읽을 수 없습니다"));
    }

    // ?page=abc 처럼 타입이 안 맞는 파라미터 — 서버 잘못이 아니라 요청 잘못 (실서버 재현: 500이 나가던 것, Codex 감사)
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, "BAD_REQUEST", "파라미터 형식이 올바르지 않습니다: " + ex.getName()));
    }

    // unique 제약 충돌 등 — "같은 요청이 동시에 두 번" 레이스의 패배 쪽. 500이 아니라 409로, 클라이언트는 재시도/재조회
    // (예: 첫 복습 답변 2건 동시 → card_progress (user, card) unique). 좋아요처럼 현재 상태로 복구하는 곳은 각자 catch
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
        log.warn("데이터 무결성 충돌 — 동시 요청 레이스 가능성: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "CONFLICT", "같은 요청이 동시에 처리되었습니다. 다시 시도해 주세요"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnExpected(Exception ex) {
        // 예상 못 한 예외가 조용히 묻히면 원인 추적 불가 — 반드시 스택트레이스 로깅
        log.error("Unexpected server error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, "INTERNAL_SERVER_ERROR", "서버 내부에 오류가 발생했습니다."));
    }
}