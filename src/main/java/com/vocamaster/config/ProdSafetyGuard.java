package com.vocamaster.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 운영 프로필 안전핀 (Phase 7 ② 보안 게이트, ADR-042).
 *
 * 원칙: 잘못 설정된 채 뜨는 것보다 안 뜨는 게 낫다 — 조건 미달이면 부팅 자체를 실패시킨다.
 * 환경변수 '누락'(${JWT_SECRET} 미해석)은 스프링이 이미 막아주지만,
 * '레포에 공개된 dev/test 시크릿을 운영에 그대로 넣는 사고'는 아무도 안 막아서 여기서 막는다.
 */
@Component
@Profile("prod")
public class ProdSafetyGuard {

    // git 히스토리에 한 번이라도 올라간 시크릿 = 이미 탄(burned) 시크릿. 운영 재사용 금지
    static final Set<String> BURNED_SECRETS = Set.of(
            "vocamaster-dev-secret-key-minimum-32-characters-long",   // application-dev.yml
            "test-secret-key-minimum-32-characters-long-for-test"     // test application.yml
    );

    public ProdSafetyGuard(@Value("${jwt.secret}") String jwtSecret) {
        if (BURNED_SECRETS.contains(jwtSecret)) {
            throw new IllegalStateException(
                    "JWT_SECRET이 레포에 공개된 dev/test 값입니다. 운영용 무작위 시크릿으로 교체하세요 "
                            + "(예: openssl rand -base64 48)");
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET이 32바이트 미만입니다. HS256 서명 키는 256비트 이상이어야 합니다");
        }
    }
}
