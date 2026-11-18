package com.thinkfree.drive.api.v1.auth.sso.response;

public record SsoCallbackResponse
(
        String token,
        String redirectUrl
)
{
}
