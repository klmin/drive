package com.thinkfree.drive.api.v1.common.loginsetting.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import jakarta.validation.constraints.NotNull;

public record SamlConfigRequest
(
    @Schema(title = "Authentication protocol", description = "Protocol discriminator for SAML settings. SAML is not supported yet; requests with saml are rejected.", type = "string", allowableValues = {"saml"})
    @NotNull
    SsoProviderProtocolType type
)
    implements SsoProtocolConfigRequest
{
}
