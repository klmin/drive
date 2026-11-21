package com.thinkfree.drive.storage.config;

import com.thinkfree.drive.storage.exception.S3ConnectionPoolTimeout;
import software.amazon.awssdk.retries.api.AcquireInitialTokenRequest;
import software.amazon.awssdk.retries.api.AcquireInitialTokenResponse;
import software.amazon.awssdk.retries.api.RecordSuccessRequest;
import software.amazon.awssdk.retries.api.RecordSuccessResponse;
import software.amazon.awssdk.retries.api.RefreshRetryTokenRequest;
import software.amazon.awssdk.retries.api.RefreshRetryTokenResponse;
import software.amazon.awssdk.retries.api.RetryStrategy;
import software.amazon.awssdk.retries.api.TokenAcquisitionFailedException;

/**
 * 커넥션 풀 대기 초과만 재시도하지 않는 재시도 전략. 나머지 판단은 감싼 전략(STANDARD)에 그대로 맡긴다.
 * <p>
 * SDK 는 풀 대기 초과도 IO 오류로 보고 재시도한다 — 풀이 이미 꽉 찬 상태에서 같은 대기를 최대 3번 반복해
 * 요청 스레드를 더 오래 잡고(기본 10초 × 3) 부하를 키운다. 막히면 바로 실패(→ 503)하게 여기서 끊는다.
 * <p>
 * {@link #useClientDefaults()} 를 false 로 둬 클라이언트 빌더가 이 객체를 다시 만들지 않게 한다
 * (true 면 빌더가 {@link #toBuilder()} 로 새 전략을 만들어 이 래퍼가 빠진다). 감싼 전략은 이미 AWS 기본 조건을 갖춘
 * {@code AwsRetryStrategy.forRetryMode(STANDARD)} 여야 한다.
 */
final class NoRetryOnPoolTimeoutStrategy implements RetryStrategy {

    private final RetryStrategy delegate;

    NoRetryOnPoolTimeoutStrategy(RetryStrategy delegate) {
        this.delegate = delegate;
    }

    @Override
    public AcquireInitialTokenResponse acquireInitialToken(AcquireInitialTokenRequest request) {
        return delegate.acquireInitialToken(request);
    }

    @Override
    public RefreshRetryTokenResponse refreshRetryToken(RefreshRetryTokenRequest request) {
        if (S3ConnectionPoolTimeout.isCause(request.failure())) {
            throw new TokenAcquisitionFailedException("S3 connection pool timeout is not retried", request.token(), request.failure());
        }
        return delegate.refreshRetryToken(request);
    }

    @Override
    public RecordSuccessResponse recordSuccess(RecordSuccessRequest request) {
        return delegate.recordSuccess(request);
    }

    @Override
    public int maxAttempts() {
        return delegate.maxAttempts();
    }

    @Override
    public boolean useClientDefaults() {
        return false;
    }

    @Override
    public Builder<?, ?> toBuilder() {
        // 빌더로 다시 만들면 풀 대기 초과 제외가 빠진다 — useClientDefaults()=false 라 SDK 는 부르지 않는다
        return delegate.toBuilder();
    }
}
