package com.thinkfree.drive.sso.provider.query;

import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class SsoProviderActiveCheckQuery {
    private Integer tenantSeq;
    private SsoProviderProtocolType protocolType;
}
