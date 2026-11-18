package com.thinkfree.drive.user.quota.alert.state.converter;

import com.thinkfree.drive.user.quota.alert.state.dto.UserQuotaAlertStateEvaluateDto;
import com.thinkfree.drive.user.quota.alert.state.entity.UserQuotaAlertState;
import org.mapstruct.Mapper;

import java.time.Instant;

@Mapper(componentModel = "spring")
public interface UserQuotaAlertStateConverter {
    
    UserQuotaAlertState toUpsertUserQuotaAlertState(Integer userSeq, Integer lastThresholdPercent, Instant registerDate, Integer updateUserSeq, Instant updateDate);
    UserQuotaAlertStateEvaluateDto toUserQuotaAlertStateEvaluateDto(Integer userSeq, Instant occurredDatetime, Integer actorUserSeq);
    
}
