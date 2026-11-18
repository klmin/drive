package com.thinkfree.drive.mfa.result;

public record MfaLoginSetupResult(
        String otpAuthUri,
        String confirmToken,
        int expiresIn
) {
}
