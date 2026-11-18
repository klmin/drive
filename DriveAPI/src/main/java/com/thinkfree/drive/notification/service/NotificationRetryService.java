package com.thinkfree.drive.notification.service;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.notification.converter.NotificationRetryConverter;
import com.thinkfree.drive.notification.dto.NotificationQuotaExceededDto;
import com.thinkfree.drive.notification.exception.NotificationErrorCode;
import com.thinkfree.drive.retry.RetryUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
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
public class NotificationRetryService {
    
    private final NotificationRetryConverter notificationRetryConverter;
    private final NotificationService notificationService;
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Retryable(
            retryFor = { ApiRuntimeException.class },
            backoff = @Backoff(delay = 500)
    )
    public void quotaExceededNotify(NotificationQuotaExceededDto dto) {
        try {
            var notificationWithEventSaveDto = notificationRetryConverter.toNotificationWithEventSaveDto(dto);
            notificationService.saveNotificationWithEvent(notificationWithEventSaveDto);
            log.info("[용량 초과] userSeq : {}, actorUserSeq : {}, occurredDatetime : {}", dto.userSeq(), dto.actorUserSeq(), dto.occurredDatetime());
        }catch(DataAccessException | ApiRuntimeException e){
            logError("Failed to quotaExceededNotify", dto.userSeq(), dto.actorUserSeq(), dto.occurredDatetime(), e);
            throw ApiRuntimeException.type(NotificationErrorCode.INSERT_FAIL);
        }
    }
    
    private void logError(String message, int userSeq, int actorUserSeq, Instant occurredDatetime, Exception e){
        log.error("[{}] retryCount: {}, userSeq: {}, actorUserSeq: {},  occurredDatetime: {}",
                    message, RetryUtil.getRetryCount(), userSeq, actorUserSeq, occurredDatetime, e);
    }
    
    @Recover
    private void quotaExceededNotifyRecover(ApiRuntimeException e, NotificationQuotaExceededDto dto){
        logError("quotaExceededNotifyRecover retry fail",  dto.userSeq(), dto.actorUserSeq(), dto.occurredDatetime(), e);
    }
    
    @Recover
    private void quotaExceededNotifyRecover(IllegalArgumentException e, NotificationQuotaExceededDto dto){
        logError("quotaExceededNotifyRecover invalid",  dto.userSeq(), dto.actorUserSeq(), dto.occurredDatetime(), e);
    }
}
