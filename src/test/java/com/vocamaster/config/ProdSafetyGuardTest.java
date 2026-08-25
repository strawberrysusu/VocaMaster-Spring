package com.vocamaster.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 순수 단위 테스트 — 컨텍스트 없이 안전핀의 '거부' 로직만.
 * (통과 경로는 ProdProfileTest가 실제 prod 컨텍스트 부팅으로 검증)
 */
class ProdSafetyGuardTest {

    @Test
    @DisplayName("레포에 공개된 dev 시크릿을 운영에 재사용 → 부팅 거부")
    void devSecretInProd_refusesToBoot() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new ProdSafetyGuard("vocamaster-dev-secret-key-minimum-32-characters-long"));
        assertTrue(e.getMessage().contains("dev/test"), "무엇이 문제인지 메시지로 알려줘야");
    }

    @Test
    @DisplayName("test 시크릿도 같은 이유로 거부 — git에 올라간 순간 이미 탄 시크릿")
    void testSecretInProd_refusesToBoot() {
        assertThrows(IllegalStateException.class,
                () -> new ProdSafetyGuard("test-secret-key-minimum-32-characters-long-for-test"));
    }

    @Test
    @DisplayName("32바이트 미만 → HS256 최소 강도 미달로 거부")
    void shortSecret_refusesToBoot() {
        assertThrows(IllegalStateException.class, () -> new ProdSafetyGuard("too-short"));
    }

    @Test
    @DisplayName("충분히 길고 공개된 적 없는 시크릿 → 통과")
    void strongSecret_passes() {
        assertDoesNotThrow(() -> new ProdSafetyGuard("k9PzX2mQ7vB4nR8tW1yE5uI0oA3sD6fG9hJ2lC5xV8bN1mQ4"));
    }
}
