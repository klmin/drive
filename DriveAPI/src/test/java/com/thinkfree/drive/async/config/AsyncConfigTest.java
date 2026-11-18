package com.thinkfree.drive.async.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 비동기 실행기 포화 정책 — @Async 리스너는 전부 커밋 뒤(@TransactionalEventListener) 제출된다.
 * <ul>
 *   <li>거부(AbortPolicy)하면 커밋된 요청이 500 이 되고 작업(스토리지 삭제·메일 등)이 사라진다.</li>
 *   <li>호출 스레드 실행(CallerRunsPolicy)은 커밋 직후 콜백 안이라, 리스너의 REQUIRED 트랜잭션이 이미 커밋된 트랜잭션에 합류해 쓰기가 사라질 수 있다.</li>
 * </ul>
 * → 큐에 자리가 날 때까지 기다렸다 넣어, 항상 작업 스레드에서 실행되게 한다.
 */
@DisplayName("AsyncConfig — 큐 포화 시 거부·호출 스레드 실행 없이, 자리가 나면 작업 스레드에서 실행")
class AsyncConfigTest {

    @Test
    @DisplayName("스레드·큐가 모두 찼을 때 제출한 작업은 기다렸다가 작업 스레드에서 실행된다(호출 스레드 아님, 예외 없음)")
    void saturatedExecutorWaitsAndRunsOnPoolThread() throws InterruptedException {
        ThreadPoolTaskExecutor executor = new AsyncConfig().getAsyncExecutor();
        CountDownLatch release = new CountDownLatch(1);
        try {
            int capacity = executor.getMaxPoolSize() + executor.getQueueCapacity();
            for (int i = 0; i < capacity; i++) {
                executor.execute(() -> {
                    try {
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            AtomicReference<Thread> ranOn = new AtomicReference<>();
            AtomicReference<Throwable> submitError = new AtomicReference<>();
            CountDownLatch ran = new CountDownLatch(1);
            Thread submitter = new Thread(() -> {
                try {
                    executor.execute(() -> {
                        ranOn.set(Thread.currentThread());
                        ran.countDown();
                    });
                } catch (Throwable t) {
                    submitError.set(t);
                }
            }, "submitter");
            submitter.start();

            submitter.join(500);
            assertThat(submitter.isAlive()).as("자리가 날 때까지 제출이 기다린다").isTrue();
            assertThat(ranOn.get()).as("호출 스레드에서 실행하지 않는다").isNull();

            release.countDown();
            assertThat(ran.await(10, TimeUnit.SECONDS)).isTrue();
            submitter.join(5000);
            assertThat(submitError.get()).isNull();
            assertThat(ranOn.get().getName()).as("작업 스레드").startsWith("DriveAsyncExecutor-");
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
