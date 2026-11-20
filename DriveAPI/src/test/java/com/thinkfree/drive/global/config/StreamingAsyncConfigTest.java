package com.thinkfree.drive.global.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StreamingAsyncConfig — 다운로드 본문 전송 스레드 풀(상한·즉시 거절·지표)")
class StreamingAsyncConfigTest {

    @Test
    @DisplayName("다운로드 본문은 DriveStream- 스레드에서 실행된다")
    void runsOnStreamThread() throws InterruptedException {
        ThreadPoolTaskExecutor executor = StreamingAsyncConfig.createExecutor(1, 2, new SimpleMeterRegistry());
        try {
            AtomicReference<String> threadName = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            executor.execute(() -> {
                threadName.set(Thread.currentThread().getName());
                done.countDown();
            });
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(threadName.get()).startsWith(StreamingAsyncConfig.THREAD_NAME_PREFIX);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("스레드가 모두 사용 중이면 기다리지 않고 바로 거절하고(→ 503) 거절 수를 센다")
    void rejectsImmediatelyWhenAllThreadsBusy() throws InterruptedException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ThreadPoolTaskExecutor executor = StreamingAsyncConfig.createExecutor(1, 2, registry);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(2);
        try {
            for (int i = 0; i < 2; i++) {
                executor.execute(() -> {
                    started.countDown();
                    try {
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

            long before = System.nanoTime();
            assertThatThrownBy(() -> executor.execute(() -> { }))
                    .isInstanceOf(TaskRejectedException.class);
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before)).isLessThan(1000);
            assertThat(registry.get(StreamingAsyncConfig.REJECTED_METRIC).counter().count()).isEqualTo(1.0);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
