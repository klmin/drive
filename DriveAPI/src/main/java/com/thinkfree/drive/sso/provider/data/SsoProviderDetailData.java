package com.thinkfree.drive.sso.provider.data;

import com.thinkfree.drive.sso.protocol.data.SsoProtocolData;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SsoProviderDetailData {
    private SsoProviderData ssoProviderData;
    private SsoProtocolData ssoProtocolData;
}
