package com.thinkfree.drive.tenant.data;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.tenant.enums.TenantStatus;
import com.thinkfree.drive.tenant.enums.TenantType;
import com.thinkfree.drive.tenant.enums.TenantUserMode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor // 공개 전체 생성자는 JPQL 생성자 식용(TenantJpaRepository#SELECT_FULL)
public class TenantData {
    private Integer tenantSeq;
    private String tenantName;
    private String domain;
    private String subdomain;
    private Integer baseDomainSeq;
    private TenantType tenantType;
    private TenantUserMode userMode;
    private TenantStatus status;
    private LoginType loginType;
    private Integer registerUserSeq;
    private String registerUserId;
    private String registerUserName;
    private Instant registerDate;
    private Integer updateUserSeq;
    private String updateUserId;
    private String updateUserName;
    private Instant updateDate;

    /** BASIC 9컬럼(감사 필드 null) — JPQL 생성자 식용(TenantJpaRepository#SELECT_BASIC) */
    public TenantData(Integer tenantSeq, String tenantName, String domain, String subdomain, Integer baseDomainSeq,
                      TenantType tenantType, TenantUserMode userMode, TenantStatus status, LoginType loginType) {
        this.tenantSeq = tenantSeq;
        this.tenantName = tenantName;
        this.domain = domain;
        this.subdomain = subdomain;
        this.baseDomainSeq = baseDomainSeq;
        this.tenantType = tenantType;
        this.userMode = userMode;
        this.status = status;
        this.loginType = loginType;
    }
}
