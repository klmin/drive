package com.thinkfree.drive.mfa.result;

public record MfaChallengeResult(
        String challengeToken,
        int expiresIn,
        String challengeType
) {
}
