package com.thinkfree.drive.auth.token.refresh.result;

public record RefreshTokenReissueResult
(
    String accessToken,
    String refreshToken
)
{
}
