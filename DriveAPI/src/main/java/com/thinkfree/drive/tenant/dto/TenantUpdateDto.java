package com.thinkfree.drive.tenant.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TenantUpdateDto {
    private Integer tenantSeq;
    private String tenantName;
    private String domain;
    private String subdomain;
    private Integer baseDomainSeq;
}
