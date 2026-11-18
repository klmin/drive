package com.thinkfree.drive.sso.protocol.oidc.dto;

import com.thinkfree.drive.sso.protocol.dto.SsoProtocolUpsertDto;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SsoOidcConfigDto {
    
    @Getter
    @Builder
    public static class Upsert implements SsoProtocolUpsertDto {
        private String issuerUrl;
        private String clientId;
        private String clientSecret;
        private String scopes;
        private String allowIdpInitiated;
    }


}
