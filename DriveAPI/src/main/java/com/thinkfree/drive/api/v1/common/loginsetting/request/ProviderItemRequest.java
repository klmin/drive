package com.thinkfree.drive.api.v1.common.loginsetting.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.sso.provider.enums.SsoProviderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProviderItemRequest
(
    @Schema(title = "Sign-in provider ID", description = "Numeric ID of the existing SSO provider being configured.")
    Integer ssoProviderSeq,
    @Schema(title = "Provider display name", description = "Name shown to users for this sign-in provider.")
    @NotBlank
    String displayName,
    @Schema(title = "Provider status", description = "ACTIVE enables this sign-in provider; INACTIVE disables it.")
    SsoProviderStatus status,
    @Schema(title = "Provider configuration", description = "Choose the provider protocol and complete its connection settings. Set type to oidc or jwt (lowercase), matching loginType.", oneOf = {JwtConfigRequest.class, OidcConfigRequest.class})
    @Valid
    @NotNull
    SsoProtocolConfigRequest config
)
{
}
