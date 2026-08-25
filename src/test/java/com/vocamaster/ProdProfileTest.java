package com.vocamaster;

import com.vocamaster.config.ProdSafetyGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 운영 프로필 배포 리허설 (Phase 7 ② 보안 게이트, ADR-042).
 *
 * 실서버에 올리기 전에 CI가 매번 확인하는 것:
 * ① application-prod.yml의 ${환경변수} 자리들이 채워지면 정말 부팅되는가 (키 오타·누락 조기 검거)
 * ② Swagger가 운영에서 정말 닫혀 있는가 (dev에선 열려 있어야 하므로 프로필 단위로만 검증 가능)
 * ③ ProdSafetyGuard가 정상 시크릿에서 통과하는가 (거부 경로는 ProdSafetyGuardTest)
 *
 * Redis는 일부러 안 띄운다 — "Redis 미구성 배포에서도 부팅은 된다"(fail-open)까지 같이 리허설.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ProdProfileTest {

    // AbstractIntegrationTest와 같은 reuse 컨테이너 — 참조하는 순간 static 블록이 start 보장
    @DynamicPropertySource
    static void prodEnv(DynamicPropertyRegistry r) {
        r.add("DB_URL", AbstractIntegrationTest.MYSQL::getJdbcUrl);
        r.add("DB_USERNAME", AbstractIntegrationTest.MYSQL::getUsername);
        r.add("DB_PASSWORD", AbstractIntegrationTest.MYSQL::getPassword);
        r.add("JWT_SECRET", () -> "prod-rehearsal-only-9f8e7d6c5b4a3210-never-deployed-anywhere");
    }

    @Autowired private MockMvc mvc;
    @Autowired private ApplicationContext context;

    @Test
    @DisplayName("prod 부팅 리허설 — 환경변수 4종이 채워지면 뜨고, 안전핀도 활성·통과")
    void bootsWithEnvVars_andGuardPasses() {
        assertNotNull(context.getBean(ProdSafetyGuard.class), "안전핀은 prod 프로필에서만 존재");
    }

    @Test
    @DisplayName("운영에서 Swagger 완전 비공개 — 명세(JSON)도 UI도 404")
    void swagger_isClosed() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/api-docs")).andExpect(status().isNotFound());
    }
}
