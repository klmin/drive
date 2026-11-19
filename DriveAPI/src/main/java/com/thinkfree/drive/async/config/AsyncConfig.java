package com.thinkfree.drive.async.config;

import com.thinkfree.drive.async.exception.AsyncExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {
    
    private static final String ASYNC_TASK_BEAN_NAME = "DriveAsyncExecutor-";
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    /** 포화 시 큐 자리를 기다리는 최대 시간 */
    private static final long QUEUE_OFFER_TIMEOUT_SECONDS = 30;
    
    @Override
    @Bean
    @Primary
    public ThreadPoolTaskExecutor getAsyncExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int corePoolSize = calculateOptimalThreadPoolSize(1, 1);
        int maxPoolSize = calculateOptimalThreadPoolSize(3, 1);
        int queSize = calculateQueueSize(200, corePoolSize);

        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queSize);
        executor.setThreadNamePrefix(ASYNC_TASK_BEAN_NAME + "Normal");
        // 제출한 스레드의 관측 컨텍스트(trace·MDC)를 작업 스레드로 넘긴다 — @Async 작업이 원래 요청 트레이스의 자식 구간이 되고
        // 로그에도 같은 traceId 가 찍힌다. 넘기지 않으면 작업 안의 SQL·S3 구간이 요청과 끊긴 별도 트레이스가 된다
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());

        // 포화 시 큐에 자리가 날 때까지 기다렸다 넣는다 — @Async 리스너는 전부 커밋 뒤(@TransactionalEventListener) 제출되므로
        // - 거부(AbortPolicy)하면 커밋된 요청이 TaskRejectedException 으로 500 이 되고 작업(스토리지 삭제·메일·알림)이 사라진다
        // - 호출 스레드 실행(CallerRunsPolicy)은 커밋 직후 콜백 안이라 리스너의 REQUIRED 트랜잭션이 이미 커밋된 트랜잭션에 합류해 쓰기가 사라질 수 있다
        // 기다려도 자리가 안 나거나(QUEUE_OFFER_TIMEOUT_SECONDS) 종료 중이면 거부한다.
        executor.setRejectedExecutionHandler((r, pool) -> {
            logging(r, pool);
            if (pool.isShutdown()) {
                throw new RejectedExecutionException("Executor is shut down");
            }
            try {
                if (!pool.getQueue().offer(r, QUEUE_OFFER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new RejectedExecutionException("Queue still full after " + QUEUE_OFFER_TIMEOUT_SECONDS + "s");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RejectedExecutionException("Interrupted while waiting for queue space", e);
            }
        });
        executor.initialize();

        return executor;
    }
    
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AsyncExceptionHandler();
    }

    private int calculateQueueSize(int taskSubmissionRate, int threadPoolSize) {
        return Math.max(100, taskSubmissionRate - threadPoolSize);
    }

    private int calculateOptimalThreadPoolSize(int ioWaitTimeSeconds, int processingTimeSeconds) {
        return (int) (CPU_COUNT * (1 + (double) ioWaitTimeSeconds / processingTimeSeconds));
    }

    private void logging(Runnable r, ThreadPoolExecutor executor){
        log.error("Task rejected. Active threads: {}, Queue size: {}, Task count: {}, Completed task count: {}",
                executor.getActiveCount(), executor.getQueue().size(), executor.getTaskCount(), executor.getCompletedTaskCount());
    }
}
