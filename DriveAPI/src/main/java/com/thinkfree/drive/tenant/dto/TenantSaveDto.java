package com.thinkfree.drive.tenant.dto;

import com.thinkfree.drive.tenant.enums.TenantType;
import com.thinkfree.drive.tenant.enums.TenantUserMode;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TenantSaveDto {
    private String tenantName;
    private String domain;
    private String subdomain;
    private Integer baseDomainSeq;
    private TenantType tenantType;
    private TenantUserMode userMode;
}
