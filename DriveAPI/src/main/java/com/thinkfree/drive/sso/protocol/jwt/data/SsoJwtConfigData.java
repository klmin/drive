package com.thinkfree.drive.sso.protocol.jwt.data;

import com.thinkfree.drive.sso.protocol.data.SsoProtocolData;
import com.thinkfree.drive.sso.protocol.jwt.enums.KeySourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@AllArgsConstructor
public class SsoJwtConfigData implements SsoProtocolData {
    private Integer ssoProviderSeq;
    private String issuer;
    private String audience;
    private KeySourceType keySourceType;
    private String publicKey;
    private String jwksUri;
    private String emailClaimName;
    private String errorRedirectUrl;
}
