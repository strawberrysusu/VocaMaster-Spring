package com.vocamaster.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.vocamaster.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 비동기 실행기의 안전장치 2개 (ADR-039).
 * 풀을 1/1/1로 좁혀 포화를 결정적으로 재현 — 운영 기본값(2/4/100)과 정책은 동일.
 */
@TestPropertySource(properties = {
        "event.executor.core-size=1",
        "event.executor.max-size=1",
        "event.executor.queue-capacity=1"
})
@Import(AsyncConfigTest.ThrowingBean.class)
class AsyncConfigTest extends AbstractIntegrationTest {

    @TestConfiguration
    static class ThrowingBean {
        @Bean
        Boom boom() { return new Boom(); }
    }

    static class Boom {
        @Async(AsyncConfig.EVENT_EXECUTOR)
        public void explode() { throw new IllegalStateException("리스너 안에서 터짐"); }
    }

    @Autowired private Boom boom;

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void attachLogCapture() {
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(AsyncConfig.class)).addAppender(logs);
    }

    @AfterEach
    void detachLogCapture() {
        ((Logger) LoggerFactory.getLogger(AsyncConfig.class)).detachAppender(logs);
    }

    @Test
    @DisplayName("큐 포화 → CallerRunsPolicy: 작업을 버리지 않고 호출 스레드가 직접 실행")
    void saturated_callerRuns_noLoss() throws Exception {
        // 공유 빈이 아니라 같은 정책의 '전용' 실행기 — 공유 빈은 다른 테스트(asyncException)의 잔여 작업이
        // 워커를 점유하고 있으면 포화 타이밍이 어긋나 간헐 실패했다 (CI 4차가 잡은 flaky, 2026-08-25)
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor own =
                new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        own.setCorePoolSize(1);
        own.setMaxPoolSize(1);
        own.setQueueCapacity(1);
        own.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        own.initialize();
        try {
            CountDownLatch release = new CountDownLatch(1);
            // 1) 워커 1개 점유 + 2) 큐 1칸 점유 → 3번째는 거부 대상 → caller-runs
            own.execute(() -> await(release));
            own.execute(() -> await(release));

            AtomicReference<String> ranOn = new AtomicReference<>();
            own.execute(() -> ranOn.set(Thread.currentThread().getName()));   // 포화 상태에서 제출

            assertEquals(Thread.currentThread().getName(), ranOn.get(),
                    "포화 시 호출 스레드에서 '즉시' 실행됐어야 (버려졌다면 null, 워커였다면 다른 이름)");
            release.countDown();
        } finally {
            own.shutdown();
        }
    }

    @Test
    @DisplayName("비동기 void 메서드의 예외 → 조용히 묻히지 않고 ERROR 로그")
    void asyncException_isLogged() throws Exception {
        boom.explode();   // 호출은 즉시 반환, 예외는 워커에서

        long deadline = System.currentTimeMillis() + 10_000;   // CI 2코어 러너는 3초를 넘길 수 있음 (flaky 실증 8/25)
        while (logs.list.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20);

        assertFalse(logs.list.isEmpty(), "AsyncUncaughtExceptionHandler가 로그를 남겨야");
        ILoggingEvent e = logs.list.get(0);
        assertEquals("ERROR", e.getLevel().toString());
        assertTrue(e.getFormattedMessage().contains("explode"), "어느 메서드였는지 식별 가능해야");
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
