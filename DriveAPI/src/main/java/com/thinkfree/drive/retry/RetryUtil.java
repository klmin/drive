package com.thinkfree.drive.retry;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.exception.ErrorCode;
import com.thinkfree.drive.quota.exception.QuotaErrorCode;
import com.thinkfree.drive.resource.aspect.exception.ResourceLockErrorCode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;

@Component
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RetryUtil {
    
    public static int getRetryCount(){
        RetryContext retryContext = getRetryContext();
        return retryContext != null ? retryContext.getRetryCount() : -1;
    }
    public static RetryContext getRetryContext(){
        return RetrySynchronizationManager.getContext();
    }

    /** 다시 해도 결과가 같은 오류 — 재시도하지 않는다(재시도마다 부수 효과가 쌓이는 것 포함, 예: 용량 초과 알림) */
    // RESOURCE_LOCKED — 버전 복원의 잠김 확인이 버전 저장(prepareMeta, @ApiRetryable) 안으로 들어와서, 잠긴 파일이면 재시도 3회 뒤에야 423 이 나던 것
    private static final Set<ErrorCode> NON_RETRYABLE = Set.of(QuotaErrorCode.IS_FULL, ResourceLockErrorCode.RESOURCE_LOCKED);

    /**
     * {@link com.thinkfree.drive.common.annotation.ApiRetryable} 의 exceptionExpression 이 부르는 판정 —
     * 결정적 오류 코드(용량 부족 등)를 가진 {@link ApiRuntimeException} 이면 false.
     */
    public static boolean isRetryable(Throwable e) {
        return !(e instanceof ApiRuntimeException api && NON_RETRYABLE.contains(api.getCode()));
    }

    /** {@code @Retryable(exceptionExpression = ...)} 에 쓰는 {@link #isRetryableOutsideTransaction} 판정식 */
    public static final String ONLY_OUTSIDE_TRANSACTION = "T(com.thinkfree.drive.retry.RetryUtil).isRetryableOutsideTransaction(#root)";

    /**
     * 바깥 트랜잭션에 합류한 시도는 재시도하지 않는다 — {@link #isRetryable} + 판정 시점에 실제 트랜잭션이 없을 것.
     * <p>
     * 재시도(@EnableRetry, LOWEST_PRECEDENCE-1)는 같은 메서드의 트랜잭션(LOWEST_PRECEDENCE) 바깥이라, 메서드가 자기 트랜잭션을 열었으면
     * 판정 시점엔 그 트랜잭션이 이미 끝나 있다(시도마다 새 트랜잭션 — 재시도 의미 있음). 판정 시점에도 트랜잭션이 살아 있으면 호출부 트랜잭션에
     * 합류한 시도다 — PG 오류 뒤엔 트랜잭션이 abort 라 재시도가 다 실패하고, 업무 예외 뒤엔 공유 트랜잭션이 rollback-only 라 "성공한" 재시도도
     * 커밋 때 UnexpectedRollbackException(500)이 된다. 그 경우는 재시도 없이 바로 @Recover(오류 코드 변환)로 넘기고, 재시도는 트랜잭션을 연 쪽(UseCase)이 맡는다.
     */
    public static boolean isRetryableOutsideTransaction(Throwable e) {
        return isRetryable(e) && !TransactionSynchronizationManager.isActualTransactionActive();
    }
}
