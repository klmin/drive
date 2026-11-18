package com.thinkfree.drive.api.v1.common.loginsetting.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OidcConfigRequest
(
    @Schema(title = "Authentication protocol", description = "Protocol discriminator. Send the lowercase value oidc for OIDC settings.", type = "string", allowableValues = {"oidc"})
    @NotNull
    SsoProviderProtocolType type,
    @Schema(title = "OIDC issuer URL", description = "Issuer URL published by the OpenID Connect provider.")
    @NotBlank
    String issuerUrl,
    @Schema(title = "OIDC client ID", description = "Client identifier assigned by the identity provider to this Drive integration.")
    @NotBlank
    String clientId,
    @Schema(title = "OIDC client secret", description = "Client secret assigned by the identity provider to this Drive integration.")
    @NotBlank
    String clientSecret
)
    implements SsoProtocolConfigRequest
{
}
