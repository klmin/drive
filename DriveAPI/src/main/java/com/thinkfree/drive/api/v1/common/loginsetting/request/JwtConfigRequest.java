package com.thinkfree.drive.api.v1.common.loginsetting.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.sso.protocol.jwt.enums.KeySourceType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record JwtConfigRequest
(
    @Schema(title = "Authentication protocol", description = "Protocol discriminator. Send the lowercase value jwt for JWT settings.", type = "string", allowableValues = {"jwt"})
    @NotNull
    SsoProviderProtocolType type,
    @Schema(title = "JWT issuer", description = "Expected issuer value (iss) in the JWT from the identity provider.")
    @NotBlank
    String issuer,
    @Schema(title = "JWT audience", description = "Expected audience value (aud) in the JWT from the identity provider.")
    String audience,
    @Schema(title = "JWT verification key source", description = "Use public_key for an entered public key or jwks for keys fetched from a JWKS endpoint.")
    @NotNull
    KeySourceType keySourceType,
    @Schema(title = "JWT public key", description = "Public verification key for tokens when keySourceType is public_key.")
    String publicKey,
    @Schema(title = "JWKS endpoint", description = "URL of the identity provider's signing-key set when keySourceType is jwks.")
    String jwksUri,
    @Schema(title = "Email claim", description = "JWT claim containing the user's email address.")
    String emailClaimName,
    @Schema(title = "Sign-in error URL", description = "Address used when the external sign-in flow fails.")
    String errorRedirectUrl
)
    implements SsoProtocolConfigRequest
{
}
