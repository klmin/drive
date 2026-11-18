package com.thinkfree.drive.user.quota.alert.state.service.impl;


import com.thinkfree.drive.user.quota.alert.state.converter.UserQuotaAlertStateConverter;
import com.thinkfree.drive.user.quota.alert.state.dto.UserQuotaAlertStateEvaluateDto;
import com.thinkfree.drive.user.quota.alert.state.entity.UserQuotaAlertState;
import com.thinkfree.drive.user.quota.alert.state.enums.QuotaThreshold;
import com.thinkfree.drive.user.quota.alert.state.exception.UserQuotaAlertStateErrorCode;
import com.thinkfree.drive.user.quota.alert.state.model.UserQuotaUsage;
import com.thinkfree.drive.user.quota.alert.state.repository.UserQuotaAlertStateRepository;
import com.thinkfree.drive.user.quota.alert.state.result.UserQuotaAlertStateUpsertResult;
import com.thinkfree.drive.user.quota.alert.state.service.UserQuotaAlertStateService;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DefaultUserQuotaAlertStateService implements UserQuotaAlertStateService {
    
    private final UserQuotaAlertStateConverter userQuotaAlertStateConverter;
    private final UserQuotaAlertStateRepository userQuotaAlertStateRepository;
    private final UserFindService userFindService;
    
    private void upsert(UserQuotaAlertState entity){
        var upsertResult = userQuotaAlertStateRepository.upsert(entity);
        DriveAssert.throwIfZero(upsertResult, UserQuotaAlertStateErrorCode.UPSERT_FAIL);
    }
    
    @Override
    public UserQuotaAlertStateUpsertResult increaseQuotaAlertState(UserQuotaAlertStateEvaluateDto dto) {
        return evaluateAndPersistInternal(dto.getUserSeq(), dto.getOccurredDatetime(), dto.getActorUserSeq());
        
    }
    
    @Override
    public void decreaseQuotaAlertState(UserQuotaAlertStateEvaluateDto dto){
        evaluateAndPersistInternal(dto.getUserSeq(), dto.getOccurredDatetime(), dto.getActorUserSeq());
    }
    
    @Override
    public void deleteByUserSeqList(List<Integer> userSeqList) {
        var result = userQuotaAlertStateRepository.deleteByUserSeqList(userSeqList);
        log.info("[유저 용량 상태 삭제 : {}건]", result);
    }
    
    private UserQuotaAlertStateUpsertResult evaluateAndPersistInternal(int userSeq, Instant occurredDatetime, int actorUserSeq){
        
        var user = userFindService.findDetailByUserSeq(userSeq);
        var currentUserQuotaUsage = UserQuotaUsage.of(user.getUseQuotaByte(), user.getTotalQuotaByte());
        var currentQuotaThreshold = currentUserQuotaUsage.threshold();
        var currentPercent = currentQuotaThreshold.getPercent();
        
        Integer lockedLastThresholdPercent = userQuotaAlertStateRepository.lockLastThresholdPercentByUserSeq(userSeq);
        var isFirstRecord = lockedLastThresholdPercent == null;

        int lastThresholdPercent = isFirstRecord ? 0 : lockedLastThresholdPercent;
        var prevQuotaThreshold = QuotaThreshold.fromPercent(lastThresholdPercent);
        
        var isHigherThan = currentUserQuotaUsage.isHigherThan(prevQuotaThreshold);
        var shouldUpdateQuotaState = isFirstRecord || !prevQuotaThreshold.equals(currentQuotaThreshold) || isHigherThan;
        
        if(shouldUpdateQuotaState) {
            var upsertUserQuotaAlertState = userQuotaAlertStateConverter.toUpsertUserQuotaAlertState(userSeq, currentPercent, occurredDatetime, actorUserSeq, occurredDatetime);
            upsert(upsertUserQuotaAlertState);
        }
        
        return new UserQuotaAlertStateUpsertResult(userSeq, currentPercent, actorUserSeq, occurredDatetime, isHigherThan);
    }
    
}
