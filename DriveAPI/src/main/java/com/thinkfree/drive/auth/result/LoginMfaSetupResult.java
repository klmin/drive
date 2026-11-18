package com.thinkfree.drive.auth.result;

import java.time.Instant;

/**
 * 로그인 도중 MFA 설정({@code AuthService.setupMfaForLogin}) 결과 — QR(otpauth URI)·확인 토큰·만료.
 * 만료 시각은 발급 시점(서비스)에 계산한다. MFA 도메인의 {@code MfaLoginSetupResult}(만료 초만 보유)를 감싼 로그인 결과.
 */
public record LoginMfaSetupResult
(
    String otpAuthUri,
    String confirmToken,
    Integer expiresIn,
    Instant expiresAt
)
{
    public static LoginMfaSetupResult of(String otpAuthUri, String confirmToken, int expiresInSeconds) {
        return new LoginMfaSetupResult(
                otpAuthUri,
                confirmToken,
                expiresInSeconds,
                Instant.now().plusSeconds(expiresInSeconds)
        );
    }
}
