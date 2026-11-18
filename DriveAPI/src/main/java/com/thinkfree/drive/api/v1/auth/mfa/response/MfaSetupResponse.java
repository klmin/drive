package com.thinkfree.drive.api.v1.auth.mfa.response;

import com.thinkfree.drive.auth.result.LoginMfaSetupResult;
import com.thinkfree.drive.global.annotation.DriveDateTimeFormat;

import java.time.Instant;

public record MfaSetupResponse(
        String otpAuthUri,
        String confirmToken,
        Integer expiresIn,
        @DriveDateTimeFormat
        Instant expiresAt
) {
    public static MfaSetupResponse from(LoginMfaSetupResult result) {
        return new MfaSetupResponse(
                result.otpAuthUri(),
                result.confirmToken(),
                result.expiresIn(),
                result.expiresAt()
        );
    }
}
