package com.thinkfree.drive.api.v1.common.loginsetting.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;

@JsonTypeInfo(
        use      = JsonTypeInfo.Id.NAME,
        include  = JsonTypeInfo.As.PROPERTY,
        property = "type",
        visible  = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = OidcConfigRequest.class, name = "oidc"),
        @JsonSubTypes.Type(value = SamlConfigRequest.class, name = "saml"),
        @JsonSubTypes.Type(value = JwtConfigRequest.class,  name = "jwt")
})
public interface SsoProtocolConfigRequest {
    @Schema(title = "Authentication protocol", description = "Protocol discriminator: oidc or jwt (lowercase), matching loginType.", type = "string", allowableValues = {"oidc", "jwt"})
    SsoProviderProtocolType type();
}
