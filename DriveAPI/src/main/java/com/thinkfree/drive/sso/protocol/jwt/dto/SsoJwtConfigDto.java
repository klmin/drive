package com.thinkfree.drive.sso.protocol.jwt.dto;

import com.thinkfree.drive.sso.protocol.dto.SsoProtocolUpsertDto;
import com.thinkfree.drive.sso.protocol.jwt.enums.KeySourceType;
import lombok.Builder;
import lombok.Getter;

public class SsoJwtConfigDto {

    @Getter
    @Builder
    public static class Upsert implements SsoProtocolUpsertDto {
        private String issuer;
        private String audience;
        private KeySourceType keySourceType;
        private String publicKey;
        private String jwksUri;
        private String emailClaimName;
        private String errorRedirectUrl;
    }
}
