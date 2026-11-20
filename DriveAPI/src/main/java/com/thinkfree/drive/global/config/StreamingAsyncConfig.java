package com.thinkfree.drive.global.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.RejectedExecutionException;

/**
 * MVC 비동기 응답(StreamingResponseBody — 파일·폴더 zip·프로필 이미지 다운로드)의 본문을 흘려보낼 스레드 풀.
 *
 * <p>설정하지 않으면 MVC 가 요청마다 새 스레드를 만드는 기본 실행기(MvcAsyncN)를 쓴다 — 상한도 지표도 없다.
 * Boot 는 {@code applicationTaskExecutor} 가 있으면 그걸 연결하지만, {@code AsyncConfig} 의 @Primary 실행기 때문에 만들지 않는다.
 * <ul>
 *   <li>스레드 하나가 다운로드 한 건을 끝까지 맡는다 — 대기열을 두면 클라이언트는 응답 헤더도 못 받은 채 기다리므로
 *       대기열 없이 최대치까지 스레드를 늘리고, 넘치면 바로 거절한다(→ 503, {@code ApiGlobalExceptionHandler})</li>
 *   <li>요청 스레드의 관측 컨텍스트(trace·MDC)를 넘겨 본문 전송 중 스토리지 호출이 요청 트레이스에 붙게 한다</li>
 *   <li>지표: 빈으로 등록돼 Boot 가 {@code executor_*{name="driveStreamExecutor"}} 를 낸다. 거절은 별도 카운터</li>
 * </ul>
 * 전송 시간 제한은 {@code spring.mvc.async.request-timeout}(application.yml) — 다운로드는 크기·회선에 따라 얼마든지 길어질 수 있어 두지 않는다.
 * 멈춘 클라이언트는 Tomcat 쓰기 타임아웃이 끊고, 동시 전송 수는 이 풀의 상한이 막는다.
 */
@Slf4j
@Configuration
public class StreamingAsyncConfig implements WebMvcConfigurer {

    public static final String THREAD_NAME_PREFIX = "DriveStream-";
    static final String REJECTED_METRIC = "drive.download.stream.rejected";

    private final AsyncTaskExecutor streamExecutor;

    public StreamingAsyncConfig(@Value("${drive.download.stream.core-threads:8}") int coreThreads,
                                @Value("${drive.download.stream.max-threads:200}") int maxThreads,
                                MeterRegistry meterRegistry) {
        this.streamExecutor = createExecutor(coreThreads, maxThreads, meterRegistry);
    }

    @Bean
    public AsyncTaskExecutor driveStreamExecutor() {
        return streamExecutor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(streamExecutor);
    }

    static ThreadPoolTaskExecutor createExecutor(int coreThreads, int maxThreads, MeterRegistry meterRegistry) {
        Counter rejected = Counter.builder(REJECTED_METRIC)
                .description("다운로드 스트리밍 스레드가 모두 사용 중이라 거절한 요청 수")
                .register(meterRegistry);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.min(coreThreads, maxThreads));
        executor.setMaxPoolSize(maxThreads);
        executor.setQueueCapacity(0); // SynchronousQueue — 대기 없이 바로 스레드에 넘기거나 거절
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.setRejectedExecutionHandler((r, pool) -> {
            rejected.increment();
            log.warn("Download stream rejected — all {} stream threads busy", pool.getMaximumPoolSize());
            throw new RejectedExecutionException("All download stream threads are busy");
        });
        // 종료 시 진행 중인 다운로드를 잠깐 기다린다(그 뒤엔 Tomcat 종료와 함께 끊긴다)
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
