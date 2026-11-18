package com.thinkfree.drive.api.v1.auth.sso.request;

import jakarta.validation.constraints.NotBlank;

public record SsoJwtLoginRequest
(
    @NotBlank
    String token
)
{
}
