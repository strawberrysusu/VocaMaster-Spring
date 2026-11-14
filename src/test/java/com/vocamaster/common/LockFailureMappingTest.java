package com.vocamaster.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 잠금 실패 → 409 (Codex 검산 2026-11-14).
 * MySQL 교착(1213)은 스프링에서 CannotAcquireLockException으로 올라온다 — 예전엔 catch-all에 걸려 500 '서버 내부 오류'였다.
 * 스프링 컨텍스트 없이 컨트롤러 하나 + GlobalExceptionHandler만 붙여 '어느 핸들러가 고르는지'까지 본다.
 */
class LockFailureMappingTest {

    @RestController
    static class ThrowingController {
        @GetMapping("/lock")
        void lock() {
            throw new CannotAcquireLockException("Deadlock found when trying to get lock; try restarting transaction");
        }
    }

    @Test
    @DisplayName("교착(CannotAcquireLockException)은 500이 아니라 409 — 다시 보내면 되는 동시 요청 충돌")
    void deadlock_is409() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        mvc.perform(get("/lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("같은 요청이 동시에 처리되었습니다. 다시 시도해 주세요"));
    }
}
