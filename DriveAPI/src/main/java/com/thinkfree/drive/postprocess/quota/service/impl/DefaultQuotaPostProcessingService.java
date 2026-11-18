package com.thinkfree.drive.postprocess.quota.service.impl;

import com.thinkfree.drive.notification.service.NotificationService;
import com.thinkfree.drive.postprocess.quota.converter.QuotaPostProcessingConverter;
import com.thinkfree.drive.postprocess.quota.dto.QuotaStateChangeNotifyDto;
import com.thinkfree.drive.postprocess.quota.dto.QuotaStateTransferDto;
import com.thinkfree.drive.postprocess.quota.service.QuotaPostProcessingService;
import com.thinkfree.drive.user.quota.alert.state.converter.UserQuotaAlertStateConverter;
import com.thinkfree.drive.user.quota.alert.state.service.UserQuotaAlertStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DefaultQuotaPostProcessingService implements QuotaPostProcessingService {
    
    private final UserQuotaAlertStateConverter userQuotaAlertStateConverter;
    private final QuotaPostProcessingConverter quotaPostProcessingConverter;
    private final UserQuotaAlertStateService userQuotaAlertStateService;
    private final NotificationService notificationService;
    
    @Override
    public void processQuotaMoveAndNotify(QuotaStateTransferDto dto){
        processQuotaIncreaseAndNotify(
            quotaPostProcessingConverter.toQuotaStateChangeNotifyDto(dto.getTargetUserSeq(), dto.getActorUserSeq(), dto.getOccurredDatetime())
        );
        processQuotaDecreaseAndNotify(
            quotaPostProcessingConverter.toQuotaStateChangeNotifyDto(dto.getUserSeq(), dto.getActorUserSeq(), dto.getOccurredDatetime())
        );
    }
    
    @Override
    public void processQuotaIncreaseAndNotify(QuotaStateChangeNotifyDto dto) {
        var userQuotaAlertStateProvidedUsageDto = userQuotaAlertStateConverter.toUserQuotaAlertStateEvaluateDto(dto.getUserSeq(), dto.getOccurredDatetime(), dto.getActorUserSeq());
        var userQuotaAlertStateUpsertResult = userQuotaAlertStateService.increaseQuotaAlertState(userQuotaAlertStateProvidedUsageDto);
        
        if (userQuotaAlertStateUpsertResult.isHigherThan()) {
            var notificationEventSaveDto = quotaPostProcessingConverter.toNotificationWithEventSaveDto(dto, userQuotaAlertStateUpsertResult.usagePercentage());
            notificationService.saveNotificationWithEvent(notificationEventSaveDto);
        }
    }
    
    @Override
    public void processQuotaDecreaseAndNotify(QuotaStateChangeNotifyDto dto) {
        var userQuotaAlertStateLiveUsageDto = userQuotaAlertStateConverter.toUserQuotaAlertStateEvaluateDto(dto.getUserSeq(), dto.getOccurredDatetime(), dto.getActorUserSeq());
        userQuotaAlertStateService.decreaseQuotaAlertState(userQuotaAlertStateLiveUsageDto);
    }
}
