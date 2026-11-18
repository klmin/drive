package com.thinkfree.drive.api.v1.auth.mfa.response;

import com.thinkfree.drive.api.v1.auth.response.AuthLoginResponse;
import com.thinkfree.drive.auth.result.AuthLoginResult;
import com.thinkfree.drive.global.annotation.DriveDateTimeFormat;
import com.thinkfree.drive.mfa.enums.MfaChallengeType;
import org.springframework.http.HttpStatus;

import java.time.Instant;

public record MfaSetupChallengeResponse
(
    MfaChallengeType mfaChallengeType,
    String token,
    Integer expiresIn,
    @DriveDateTimeFormat Instant expiresAt
)
    implements AuthLoginResponse
{

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.PRECONDITION_REQUIRED;
    }

    public static MfaSetupChallengeResponse from(AuthLoginResult.MfaSetupChallenge result) {
        return new MfaSetupChallengeResponse(
                result.mfaChallengeType(),
                result.token(),
                result.expiresIn(),
                result.expiresAt()
        );
    }
}
