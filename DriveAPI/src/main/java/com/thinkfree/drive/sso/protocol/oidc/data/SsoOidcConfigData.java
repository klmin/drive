package com.thinkfree.drive.sso.protocol.oidc.data;

import com.thinkfree.drive.sso.protocol.data.SsoProtocolData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@AllArgsConstructor
public class SsoOidcConfigData implements SsoProtocolData {
    private Integer ssoProviderSeq;
    private String issuerUrl;
    private String clientId;
    private String clientSecret;
    private String scopes;
    private Boolean allowIdpInitiated;
}
