package com.thinkfree.drive.api.v1.auth.mfa.response;

import com.thinkfree.drive.api.v1.auth.response.AuthLoginResponse;
import com.thinkfree.drive.auth.result.AuthLoginResult;
import com.thinkfree.drive.global.annotation.DriveDateTimeFormat;
import com.thinkfree.drive.mfa.enums.MfaChallengeType;
import org.springframework.http.HttpStatus;

import java.time.Instant;

public record MfaChallengeResponse
(
    MfaChallengeType mfaChallengeType,
    String token,
    Integer expiresIn,
    @DriveDateTimeFormat Instant expiresAt,
    String challengeType
)
    implements AuthLoginResponse
{

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.PRECONDITION_REQUIRED;
    }

    public static MfaChallengeResponse from(AuthLoginResult.MfaChallenge result) {
        return new MfaChallengeResponse(
                result.mfaChallengeType(),
                result.token(),
                result.expiresIn(),
                result.expiresAt(),
                result.challengeType()
        );
    }
}
