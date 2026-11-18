package com.thinkfree.drive.user.quota.alert.state.service;

import com.thinkfree.drive.user.quota.alert.state.dto.UserQuotaAlertStateEvaluateDto;
import com.thinkfree.drive.user.quota.alert.state.result.UserQuotaAlertStateUpsertResult;

import java.util.List;

public interface UserQuotaAlertStateService {
    UserQuotaAlertStateUpsertResult increaseQuotaAlertState(UserQuotaAlertStateEvaluateDto dto);
    void decreaseQuotaAlertState(UserQuotaAlertStateEvaluateDto dto);
    void deleteByUserSeqList(List<Integer> userSeqList);
}
