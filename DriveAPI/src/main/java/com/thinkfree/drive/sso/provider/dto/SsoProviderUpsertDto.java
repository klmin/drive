package com.thinkfree.drive.sso.provider.dto;

import com.thinkfree.drive.sso.protocol.dto.SsoProtocolUpsertDto;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import com.thinkfree.drive.sso.provider.enums.SsoProviderStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SsoProviderUpsertDto {
    private Integer ssoProviderSeq;
    private Integer tenantSeq;
    private String displayName;
    private String iconImageKey;
    private Integer sortOrder;
    private SsoProviderStatus status;
    private SsoProviderProtocolType protocolType;
    private SsoProviderScope scope;
    private SsoProtocolUpsertDto protocolUpsertDto;
}
