package com.thinkfree.drive.postprocess.orchestrator;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.postprocess.orchestrator.dto.*;
import com.thinkfree.drive.postprocess.orchestrator.exception.PostProcessingErrorCode;
import com.thinkfree.drive.quota.exception.QuotaException;
import com.thinkfree.drive.retry.RetryUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 후처리 + 오류 코드 변환. 재시도는 트랜잭션 밖에서 불렸을 때만(RetryUtil.ONLY_OUTSIDE_TRANSACTION) — 클래스 @Transactional 이 REQUIRED 라
 * UseCase 트랜잭션 안에서 부르면 합류하고, 합류한 재시도는 무의미(PG abort)하거나 커밋 때 UnexpectedRollbackException(500)이 된다(10-14 점검 2-b).
 * 합류 중 실패는 재시도 없이 @Recover 로 같은 오류 코드가 되고, 다시 시도할지는 트랜잭션을 연 쪽이 정한다(예: VersionUseCase 의 트랜잭션 전체 재시도).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PostProcessingOrchestratorRetry {
    
    private final PostProcessingOrchestrator postProcessingOrchestrator;
    
    @Retryable(
            retryFor = { ApiRuntimeException.class },
            exceptionExpression = RetryUtil.ONLY_OUTSIDE_TRANSACTION,
            backoff = @Backoff(delay = 500)
    )
    public void processAfterUploadAndNotify(PostProcessUploadDto dto) {
        try {
            postProcessingOrchestrator.processAfterUploadAndNotify(dto);
        }catch(DataAccessException | ApiRuntimeException e){
            logError("Failed to processAfterUploadAndNotify", dto.userSeq(), dto.actorUserSeq(), dto.resourceSeq(), dto.occurredDatetime(), e);
            throw ApiRuntimeException.type(PostProcessingErrorCode.POST_PROCESSING_FAILED);
        }
    }
    
    @Retryable(
            retryFor = { ApiRuntimeException.class },
            exceptionExpression = RetryUtil.ONLY_OUTSIDE_TRANSACTION,
            backoff = @Backoff(delay = 500)
    )
    public void processQuotaIncreaseAndNotify(PostProcessQuotaChangeDto dto) {
        try {
            postProcessingOrchestrator.processQuotaIncreaseAndNotify(dto);
        }catch(DataAccessException | ApiRuntimeException e){
            logError("Failed to processQuotaIncreaseAndNotify", dto.userSeq(), dto.actorUserSeq(), dto.occurredDatetime(), e);
            throw ApiRuntimeException.type(PostProcessingErrorCode.QUOTA_STATE_EVALUATION_FAILED);
        }
    
    }
    
    @Retryable(
            retryFor = { ApiRuntimeException.class },
            exceptionExpression = RetryUtil.ONLY_OUTSIDE_TRANSACTION,
            backoff = @Backoff(delay = 500)
    )
    public void processQuotaDecreaseAndNotify(PostProcessQuotaChangeDto dto) {
        try {
            postProcessingOrchestrator.processQuotaDecreaseAndNotify(dto);
        }catch(DataAccessException | ApiRuntimeException e){
            logError("Failed to processQuotaDecreaseAndNotify", dto.userSeq(), dto.actorUserSeq(), dto.occurredDatetime(), e);
            throw ApiRuntimeException.type(PostProcessingErrorCode.QUOTA_STATE_UPSERT_FAILED);
        }
    }
    
    @Retryable(
            retryFor = { ApiRuntimeException.class },
            exceptionExpression = RetryUtil.ONLY_OUTSIDE_TRANSACTION,
            noRetryFor = { QuotaException.class },
            backoff = @Backoff(delay = 500)
    )
    public void processMoveAndNotify(PostProcessMoveDto dto) {
        try {
            postProcessingOrchestrator.processMoveAndNotify(dto);
        }catch(DataAccessException | ApiRuntimeException e){
            logError("Failed to processQuotaMoveAndNotify", dto.userSeq(), dto.actorUserSeq(), dto.targetUserSeq(), dto.occurredDatetime(), e);
            throw ApiRuntimeException.type(PostProcessingErrorCode.MOVE_NOTIFY_CREATE_FAILED);
        }
    }
    
    @Retryable(
            retryFor = { ApiRuntimeException.class },
            exceptionExpression = RetryUtil.ONLY_OUTSIDE_TRANSACTION,
            backoff = @Backoff(delay = 500)
    )
    public void processTrashAndNotify(PostProcessTrashDto dto) {
        try {
            postProcessingOrchestrator.processTrashAndNotify(dto);
        }catch(DataAccessException | ApiRuntimeException e){
            logError("Failed to processTrashAndNotify", dto.userSeq(), dto.actorUserSeq(), dto.occurredDatetime(), e);
            throw ApiRuntimeException.type(PostProcessingErrorCode.TRASH_NOTIFY_CREATE_FAILED);
        }
    }
    
    @Retryable(
            retryFor = { ApiRuntimeException.class },
            exceptionExpression = RetryUtil.ONLY_OUTSIDE_TRANSACTION,
            backoff = @Backoff(delay = 500)
    )
    public void processSharedAndNotify(PostProcessShareDto dto) {
        try {
            postProcessingOrchestrator.processSharedAndNotify(dto);
        }catch(DataAccessException | ApiRuntimeException e){
            logError("Failed to processSharedAndNotify", dto.userSeq(), dto.actorUserSeq(), dto.resourceSeq(), dto.occurredDatetime(), e);
            throw ApiRuntimeException.type(PostProcessingErrorCode.SHARING_NOTIFY_CREATE_FAILED);
        }
    }
    
    @Retryable(
            retryFor = { ApiRuntimeException.class },
            exceptionExpression = RetryUtil.ONLY_OUTSIDE_TRANSACTION,
            backoff = @Backoff(delay = 500)
    )
    public void processUnsharedAndNotify(PostProcessUnshareDto dto) {
        try {
            postProcessingOrchestrator.processUnsharedAndNotify(dto);
        }catch(DataAccessException | ApiRuntimeException e){
            logError("Failed to processUnsharedAndNotify", dto.userSeq(), dto.actorUserSeq(), dto.resourceSeq(), dto.occurredDatetime(), e);
            throw ApiRuntimeException.type(PostProcessingErrorCode.UNSHARING_NOTIFY_CREATE_FAILED);
        }
    }
    @Retryable(
            retryFor = { ApiRuntimeException.class },
            exceptionExpression = RetryUtil.ONLY_OUTSIDE_TRANSACTION,
            backoff = @Backoff(delay = 500)
    )
    public void processShareTargetChangedAndNotify(PostProcessShareTargetChangeDto dto) {
        try {
            postProcessingOrchestrator.processShareTargetChangedAndNotify(dto);
        }catch(DataAccessException | ApiRuntimeException e){
            logError("Failed to processShareTargetChangedAndNotify", dto.userSeq(), dto.actorUserSeq(), dto.resourceSeq(), dto.occurredDatetime(), e);
            throw ApiRuntimeException.type(PostProcessingErrorCode.SHARING_NOTIFY_CREATE_FAILED);
        }
    }
    
    private void logError(String message, int userSeq, int actorUserSeq, Long resourceSeq, Instant occurredDatetime, Exception e){
        log.error("[{}] retryCount: {}, userSeq: {}, actorUserSeq: {}, resourceSeq: {}, occurredDatetime : {}",
                    message, RetryUtil.getRetryCount(), userSeq, actorUserSeq, resourceSeq, occurredDatetime, e);
    }
    private void logError(String message, int userSeq, int actorUserSeq, int targetUserSeq, Instant occurredDatetime, Exception e){
        log.error("[{}] retryCount: {}, userSeq: {}, actorUserSeq: {}, resourceSeq: {}, occurredDatetime : {}",
                    message, RetryUtil.getRetryCount(), userSeq, actorUserSeq, targetUserSeq, occurredDatetime, e);
    }
    private void logError(String message, int userSeq, int actorUserSeq, Instant occurredDatetime, Exception e){
        log.error("[{}] retryCount: {}, userSeq: {}, actorUserSeq: {},  occurredDatetime: {}",
                    message, RetryUtil.getRetryCount(), userSeq, actorUserSeq, occurredDatetime, e);
    }
    
    @Recover
    private void processAfterUploadAndNotifyRecover(ApiRuntimeException e, PostProcessUploadDto dto){
        logError("processAfterUploadAndNotify retry fail", dto.userSeq(), dto.actorUserSeq(), dto.resourceSeq(), dto.occurredDatetime(), e);
        throw e;
    }
    
    @Recover
    private void processAfterUploadAndNotifyRecover(IllegalArgumentException e, PostProcessUploadDto dto){
        logError("processAfterUploadAndNotify invalid", dto.userSeq(), dto.actorUserSeq(), dto.resourceSeq(), dto.occurredDatetime(), e);
        throw ApiRuntimeException.type(PostProcessingErrorCode.REQUIRED_FIELD_MISSING);
    }
    
    @Recover
    private void processQuotaChangeAndNotifyRecover(ApiRuntimeException e, PostProcessQuotaChangeDto dto){
        logError("processQuotaChangeAndNotify retry fail", dto.userSeq(), dto.actorUserSeq(), dto.occurredDatetime(), e);
        throw e;
    }
    
    @Recover
    private void processQuotaChangeAndNotifyRecover(IllegalArgumentException e, PostProcessQuotaChangeDto dto){
        logError("processQuotaChangeAndNotify invalid", dto.userSeq(), dto.actorUserSeq(), dto.occurredDatetime(), e);
        throw ApiRuntimeException.type(PostProcessingErrorCode.REQUIRED_FIELD_MISSING);
    }
    
    @Recover
    private void processQuotaMoveAndNotifyRecover(ApiRuntimeException e, PostProcessMoveDto dto){
        logError("processQuotaMoveAndNotify retry fail", dto.userSeq(), dto.actorUserSeq(), dto.targetUserSeq(), dto.occurredDatetime(), e);
        throw e;
    }
    
    @Recover
    private void processQuotaMoveAndNotifyRecover(IllegalArgumentException e, PostProcessMoveDto dto){
        logError("processQuotaMoveAndNotify invalid", dto.userSeq(), dto.actorUserSeq(), dto.targetUserSeq(), dto.occurredDatetime(), e);
        throw ApiRuntimeException.type(PostProcessingErrorCode.REQUIRED_FIELD_MISSING);
    }
    
    @Recover
    private void processQuotaMoveAndNotifyRecover(QuotaException e, PostProcessMoveDto dto){
        logError("processQuotaMoveAndNotify quota is full", dto.userSeq(), dto.actorUserSeq(), dto.targetUserSeq(), dto.occurredDatetime(), e);
        throw ApiRuntimeException.type(e.getCode());
    }
    
    @Recover
    private void processTrashAndNotifyRecover(ApiRuntimeException e, PostProcessTrashDto dto){
        logError("processTrashAndNotify retry fail", dto.userSeq(), dto.userSeq(), dto.actorUserSeq(), dto.occurredDatetime(), e);
        throw e;
    }
    
    @Recover
    private void processTrashAndNotifyRecover(IllegalArgumentException e, PostProcessTrashDto dto){
        logError("processTrashAndNotifyRecover invalid", dto.userSeq(), dto.actorUserSeq(), dto.occurredDatetime(), e);
        throw ApiRuntimeException.type(PostProcessingErrorCode.REQUIRED_FIELD_MISSING);
    }
    
    @Recover
    private void processShareAndNotifyRecover(ApiRuntimeException e, PostProcessShareDto dto){
        logError("processShareAndNotifyRecover retry fail", dto.userSeq(), dto.actorUserSeq(), dto.resourceSeq(), dto.occurredDatetime(), e);
        throw e;
    }
    
    @Recover
    private void processShareAndNotifyRecover(IllegalArgumentException e, PostProcessShareDto dto){
        logError("processShareAndNotifyRecover invalid", dto.userSeq(), dto.actorUserSeq(), dto.resourceSeq(), dto.occurredDatetime(), e);
        throw ApiRuntimeException.type(PostProcessingErrorCode.REQUIRED_FIELD_MISSING);
    }
    
    @Recover
    private void processUnshareAndNotifyRecover(ApiRuntimeException e, PostProcessUnshareDto dto){
        logError("processUnshareAndNotifyRecover retry fail",  dto.userSeq(), dto.actorUserSeq(), dto.resourceSeq(), dto.occurredDatetime(), e);
        throw e;
    }
    
    @Recover
    private void processUnshareAndNotifyRecover(IllegalArgumentException e, PostProcessUnshareDto dto){
        logError("processUnshareAndNotifyRecover invalid",  dto.userSeq(), dto.actorUserSeq(), dto.resourceSeq(), dto.occurredDatetime(), e);
        throw ApiRuntimeException.type(PostProcessingErrorCode.REQUIRED_FIELD_MISSING);
    }
    
    @Recover
    private void processShareTargetChangeAndNotifyRecover(ApiRuntimeException e, PostProcessShareTargetChangeDto dto){
        logError("processUnshareAndNotifyRecover retry fail",  dto.userSeq(), dto.actorUserSeq(), dto.resourceSeq(), dto.occurredDatetime(), e);
        throw e;
    }
    
    @Recover
    private void processShareTargetChangeAndNotifyRecover(IllegalArgumentException e, PostProcessShareTargetChangeDto dto){
        logError("processUnshareAndNotifyRecover invalid",  dto.userSeq(), dto.actorUserSeq(), dto.resourceSeq(), dto.occurredDatetime(), e);
        throw ApiRuntimeException.type(PostProcessingErrorCode.REQUIRED_FIELD_MISSING);
    }
    
}
