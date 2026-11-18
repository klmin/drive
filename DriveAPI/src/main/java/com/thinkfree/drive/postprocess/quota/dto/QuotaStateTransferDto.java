package com.thinkfree.drive.postprocess.quota.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class QuotaStateTransferDto {
    private Integer userSeq;
    private Integer targetUserSeq;
    private Integer actorUserSeq;
    private Instant occurredDatetime;
}
