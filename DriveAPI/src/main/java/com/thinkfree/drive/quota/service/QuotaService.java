package com.thinkfree.drive.quota.service;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.notification.dto.NotificationQuotaExceededDto;
import com.thinkfree.drive.notification.service.NotificationRetryService;
import com.thinkfree.drive.quota.exception.QuotaErrorCode;
import com.thinkfree.drive.quota.exception.QuotaException;
import com.thinkfree.drive.retry.RetryUtil;
import com.thinkfree.drive.storage.result.StorageCapacityResult;
import com.thinkfree.drive.storage.service.StorageAdminService;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;


@Service
@RequiredArgsConstructor
@Slf4j
public class QuotaService {

    private final UserService userService;
    private final NotificationRetryService notificationRetryService;
    private final StorageAdminService storageAdminService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserveQuota(Long additionalBytes, int userSeq, int actorUserSeq) {

        validateQuota(additionalBytes, userSeq, actorUserSeq);
        
        try {
            userService.increaseUseQuotaByte(additionalBytes, userSeq);
        }catch(ApiRuntimeException e){
            log.error("[Failed to reserveQuota - increaseUseQuotaByte] retryCount : {}, userSeq: {}, additionalBytes: {}", RetryUtil.getRetryCount(), userSeq, additionalBytes);
            throw ApiRuntimeException.type(QuotaErrorCode.UPDATE_FAIL);
        }
        
        updateQuotaByteLog("increaseUseQuotaByte", userSeq, additionalBytes);
    }
    
    @Transactional
    public void reserveQuotaInTx(Long additionalBytes, int userSeq, int actorUserSeq) {
        validateQuota(additionalBytes, userSeq, actorUserSeq);
        userService.increaseUseQuotaByte(additionalBytes, userSeq);
    }
    
    private void validateQuota(Long additionalBytes, int userSeq, int actorUserSeq){
        
        User user = userService.lockUserBySeq(userSeq);

        if(user.wouldExceedQuota(additionalBytes)) {
            quotaExceededNotify(userSeq, actorUserSeq);
            throw ApiRuntimeException.type(QuotaErrorCode.IS_FULL);
        }
    }

    @Retryable(
            retryFor = { ApiRuntimeException.class },
            backoff = @Backoff(delay = 500)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decreaseUseQuotaByte(Long additionalBytes, int userSeq) {
        try {
            userService.decreaseUseQuotaByte(additionalBytes, userSeq);
        }catch(ApiRuntimeException e){
            log.error("[Failed to decrease quota] retryCount : {}, userSeq: {}, additionalBytes: {}", RetryUtil.getRetryCount(), userSeq, additionalBytes);
            throw ApiRuntimeException.type(QuotaErrorCode.DECREASE_FAIL);
        }

        updateQuotaByteLog("decreaseUseQuotaByte", userSeq, additionalBytes);
    }
    
    @Transactional
    public void moveQuota(Long additionalBytes, int userSeq, int targetUserSeq, int actorUserSeq){
        
        User user = userService.lockUserBySeq(targetUserSeq);
        
        if(user.wouldExceedQuota(additionalBytes)) {
            quotaExceededNotify(targetUserSeq, actorUserSeq);
            throw new QuotaException(QuotaErrorCode.IS_FULL);
        }
        
        userService.increaseUseQuotaByte(additionalBytes, targetUserSeq);
        userService.decreaseUseQuotaByte(additionalBytes, userSeq);
        
        updateQuotaByteLog("increaseUseQuotaByte", targetUserSeq, additionalBytes);
        updateQuotaByteLog("decreaseUseQuotaByte", userSeq, additionalBytes);
    }
    
    public boolean canIncrease(long requestedQuotaBytes){
        
        if(requestedQuotaBytes < 0){
            throw new IllegalArgumentException("additionalBytes must be >= 0");
        }
        
        if(requestedQuotaBytes == 0) {
            return true;
        }
        
        StorageCapacityResult storageCapacityResult = storageAdminService.getStorageCapacity();
        
        return switch(storageCapacityResult.capacityMode()){
            case UNLIMITED -> true;
            case LIMITED -> storageCapacityResult.totalAvailBytes() >= requestedQuotaBytes;
        };
        
    }
    
    
    
    /** 할당량(무제한 없음, 역할별 값은 호출부가 TenantQuotaPolicy.resolveUserQuota 로 확정)만큼 스토리지 가용량이 있는지 */
    public void ensureCanAssignQuotaIncrease(long requestedQuotaBytes) {
        if (!canIncrease(requestedQuotaBytes)) {
            throw new ApiRuntimeException(QuotaErrorCode.STORAGE_CAPACITY_INSUFFICIENT);
        }
    }

    public void ensureCanUpdateQuota(long newQuota, long currentQuota, long useQuotaByte) {
        if (newQuota <= currentQuota) {
            return;
        }

        long requiredQuota = newQuota - useQuotaByte;
        if (!canIncrease(requiredQuota)) {
            throw new ApiRuntimeException(QuotaErrorCode.STORAGE_CAPACITY_INSUFFICIENT);
        }
    }
    
    @Recover
    public void recoverQuotaOperation(RuntimeException e, Long additionalBytes, int userSeq){
        log.error("[Quota Operation Failed] userSeq: {}, additionalBytes: {}, retryCount: {}, error: {}",
            userSeq, additionalBytes, RetryUtil.getRetryCount(), e.getMessage());
    }

    private void updateQuotaByteLog(String message, int userSeq, Long additionalBytes){
        log.info("[{}] userSeq : {}, additionalBytes : {}", message, userSeq, additionalBytes);
    }
    
    private void quotaExceededNotify(int userSeq, int actorUserSeq){
        var now = Instant.now();
        var notificationQuotaExceededDto = new NotificationQuotaExceededDto(userSeq, actorUserSeq, now);
        notificationRetryService.quotaExceededNotify(notificationQuotaExceededDto);
    }
}
