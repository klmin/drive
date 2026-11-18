package com.thinkfree.drive.auth.result;

public record LoginTokenResult
(
    String accessToken,
    String refreshToken
)
{
}
