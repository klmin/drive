package com.thinkfree.drive.sso.provider.data;

import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import com.thinkfree.drive.sso.provider.enums.SsoProviderStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor // 공개 전체 생성자(FULL 17컬럼)는 JPQL 생성자 식용(SsoProviderJpaRepository#FULL_HEAD)
public class SsoProviderData {

    /** BASIC 9컬럼(감사 없음) — JPQL 생성자 식용(SsoProviderJpaRepository#findBasicByTenantAndProtocol) */
    public SsoProviderData(Integer ssoProviderSeq, Integer tenantSeq, String displayName, String tenantName, String iconImageKey,
                           SsoProviderProtocolType protocolType, SsoProviderStatus status, SsoProviderScope scope, Integer sortOrder) {
        this(ssoProviderSeq, tenantSeq, displayName, tenantName, iconImageKey, protocolType, status, scope, sortOrder,
                null, null, null, null, null, null, null, null);
    }

    private Integer ssoProviderSeq;
    private Integer tenantSeq;
    private String displayName;
    private String tenantName;
    private String iconImageKey;
    private SsoProviderProtocolType protocolType;
    private SsoProviderStatus status;
    private SsoProviderScope scope;
    private Integer sortOrder;
    private Integer registerUserSeq;
    private String registerUserName;
    private String registerUserId;
    private Instant registerDate;
    private Integer updateUserSeq;
    private String updateUserName;
    private String updateUserId;
    private Instant updateDate;
}
