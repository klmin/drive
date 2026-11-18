package com.thinkfree.drive.sso.protocol.data;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SsoLoginConfigData implements SsoProtocolData {
    private Integer ssoProviderSeq;
    private String errorRedirectUrl;
}
