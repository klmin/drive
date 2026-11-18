package com.thinkfree.drive.api.v1.auth.sso.request;

import jakarta.validation.constraints.NotBlank;

public record SsoSignupRequest
(
    @NotBlank
    String e,
    @NotBlank
    String n,
    @NotBlank
    String st
)
{
}
