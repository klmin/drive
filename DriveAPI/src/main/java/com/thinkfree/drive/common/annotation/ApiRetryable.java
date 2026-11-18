package com.thinkfree.drive.common.annotation;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Retryable(
    retryFor = {
        ApiRuntimeException.class
    },
    // 결정적 오류(용량 부족 등)는 재시도 안 함 — 재시도마다 용량 초과 알림이 쌓이던 문제(5차 패스). 판정 = RetryUtil.isRetryable
    exceptionExpression = "T(com.thinkfree.drive.retry.RetryUtil).isRetryable(#root)",
    backoff = @Backoff(delay = 500)
)
public @interface ApiRetryable {
}
