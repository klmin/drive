package com.thinkfree.drive.user.quota.alert.state.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Builder
@Getter
public class UserQuotaAlertStateEvaluateDto {
    private Integer userSeq;
    private Instant occurredDatetime;
    private Integer actorUserSeq;
}
