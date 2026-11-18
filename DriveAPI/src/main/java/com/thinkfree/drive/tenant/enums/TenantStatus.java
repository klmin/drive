package com.thinkfree.drive.tenant.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum TenantStatus {
    ACTIVE,
    INACTIVE
    ;
}
