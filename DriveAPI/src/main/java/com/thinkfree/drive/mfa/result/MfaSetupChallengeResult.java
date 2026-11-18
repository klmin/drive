package com.thinkfree.drive.mfa.result;

public record MfaSetupChallengeResult(
        String setupToken,
        int expiresIn
) {
}
