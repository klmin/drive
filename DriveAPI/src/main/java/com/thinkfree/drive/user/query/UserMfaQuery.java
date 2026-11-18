package com.thinkfree.drive.user.query;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Builder
@Getter
public class UserMfaQuery{
    private Integer userSeq;
    private Integer tenantSeq;
    private String mfaSecretKey;
    private Instant mfaSetupDate;
    private Instant mfaConfirmDate;
    private Instant cutoffAt;
    private Integer updateUserSeq;
    private Instant updateDate;
}


