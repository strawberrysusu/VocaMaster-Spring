package com.vocamaster.auth;

import com.vocamaster.common.exception.UnauthorizedException;

/**
 * Refresh token 재사용 감지 신호 (P1-1).
 *
 * 감지 트랜잭션 "안"에서는 제재(mass logout)를 실행하지 않고 이 예외로 사실만 알린다 —
 * 트랜잭션이 잡은 행 락과 제재 UPDATE가 충돌해 부모-자식 데드락이 나기 때문.
 * 바깥(트랜잭션 종료 = 락 해제 이후)의 catch가 제재를 실행한다.
 *
 * UnauthorizedException을 상속하므로 어디서도 안 잡히면 그대로 401 — fail-safe.
 */
public class ReuseDetectedException extends UnauthorizedException {

    private final Long userId;

    public ReuseDetectedException(Long userId) {
        super("유효하지 않은 토큰입니다");
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }
}
