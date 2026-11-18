package com.thinkfree.drive.api.v1.auth.sso.request;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.RequestParam;

public record SsoOidcCallbackRequest
(
    @NotBlank
    @RequestParam
    String code,
    @NotBlank
    @RequestParam
    String state
)
{
}
