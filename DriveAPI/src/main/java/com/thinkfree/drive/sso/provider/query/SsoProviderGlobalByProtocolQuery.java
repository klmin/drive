package com.thinkfree.drive.sso.provider.query;

import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderStatus;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class SsoProviderGlobalByProtocolQuery {
    private SsoProviderProtocolType protocolType;
    private SsoProviderStatus status;
}
