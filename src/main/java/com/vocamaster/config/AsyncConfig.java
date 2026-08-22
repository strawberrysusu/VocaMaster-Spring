package com.vocamaster.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 이벤트 리스너용 비동기 실행기 (ADR-039).
 *
 * 정책 — "복구 가능한 것만 비동기로":
 *  - 비동기 대상: 요약 캐시 삭제 (유실돼도 TTL 5분이 상한, 다음 조회가 DB로 재생성)
 *  - 동기 유지: 출석부 INSERT·study_count (원본 기록 — 유실되면 복구 근거가 없음)
 *
 * 안전장치 2개:
 *  - 큐 포화 시 CallerRunsPolicy: 작업을 버리지 않고 호출 스레드가 직접 실행 (응답은 느려져도 유실 0)
 *  - 비동기 예외는 AsyncUncaughtExceptionHandler가 ERROR 로그 — void 메서드의 예외는 기본적으로 아무 데도 안 나타남
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    public static final String EVENT_EXECUTOR = "eventExecutor";

    @Value("${event.executor.core-size:2}") private int coreSize;
    @Value("${event.executor.max-size:4}") private int maxSize;
    @Value("${event.executor.queue-capacity:100}") private int queueCapacity;

    @Bean(EVENT_EXECUTOR)
    public Executor eventExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(coreSize);
        ex.setMaxPoolSize(maxSize);
        ex.setQueueCapacity(queueCapacity);
        ex.setThreadNamePrefix("event-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.setWaitForTasksToCompleteOnShutdown(true);   // 종료 시 큐에 남은 캐시 삭제를 마저 처리 (정상 종료 한정)
        ex.setAwaitTerminationSeconds(10);
        ex.initialize();
        return ex;
    }

    @Override
    public Executor getAsyncExecutor() {
        return eventExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error("비동기 리스너 예외 — 조용히 묻히지 않게 기록: {}.{}({})",
                method.getDeclaringClass().getSimpleName(), method.getName(), Arrays.toString(params), ex);
    }
}
