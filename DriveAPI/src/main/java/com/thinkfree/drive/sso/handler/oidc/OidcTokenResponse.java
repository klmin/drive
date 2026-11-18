package com.thinkfree.drive.sso.handler.oidc;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OidcTokenResponse(
        String accessToken,
        String tokenType,
        String idToken,
        Long expiresIn,
        String scope
)
{
}
