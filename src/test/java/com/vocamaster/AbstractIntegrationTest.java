package com.vocamaster;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;

/**
 * 모든 통합 테스트의 공통 베이스 (ADR-025).
 *
 * - MySQL 8 컨테이너를 *클래스 로딩 시 1회* 수동 start → 모든 테스트가 공유
 * - {@code @DynamicPropertySource}로 datasource 명시 주입 (전통 패턴, 호환성 ↑)
 * - {@code @ServiceConnection} 미사용 — Spring Boot 3.3.0 + Testcontainers 1.21.3 모듈 충돌 회피
 * - Flyway가 실제 마이그레이션 실행 → 운영과 동일 스키마 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest {

    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.32")
            .withDatabaseName("vocamaster_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    static {
        MYSQL.start();                  // 클래스 로딩 시 1회 시작 (@Container 어노테이션 대신)
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }
}
