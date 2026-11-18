package com.thinkfree.drive.api.v1.auth.sso.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thinkfree.drive.global.annotation.DriveDateTimeFormat;
import com.thinkfree.drive.mfa.enums.MfaChallengeType;
import com.thinkfree.drive.user.enums.DefaultLandingPage;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserLanguage;
import org.springframework.http.HttpStatus;

import java.util.List;

import java.time.Instant;

public sealed interface SsoJwtLoginResponse
        permits SsoJwtLoginResponse.Login, SsoJwtLoginResponse.Mfa, SsoJwtLoginResponse.MfaSetup, SsoJwtLoginResponse.AlreadyLoggedIn {

    default HttpStatus httpStatus() {
        return HttpStatus.OK;
    }

    record Login
    (
        Integer userSeq,
        String userId,
        String userName,
        Long totalQuotaByte,
        Long useQuotaByte,
        List<RoleType> roles,
        UserLanguage userLanguage,
        DefaultLandingPage defaultLandingPage,
        @JsonIgnore String accessToken,
        @JsonIgnore String refreshToken
    )
        implements SsoJwtLoginResponse {}

    record Mfa
    (
        MfaChallengeType mfaChallengeType,
        String token,
        Integer expiresIn,
        @DriveDateTimeFormat
        Instant expiresAt,
        String challengeType
    )
        implements SsoJwtLoginResponse
    {
        @Override
        public HttpStatus httpStatus() {
            return HttpStatus.PRECONDITION_REQUIRED;
        }
    }

    record MfaSetup
    (
        MfaChallengeType mfaChallengeType,
        String token,
        Integer expiresIn,
        @DriveDateTimeFormat
        Instant expiresAt
    )
        implements SsoJwtLoginResponse
    {
        @Override
        public HttpStatus httpStatus() {
            return HttpStatus.PRECONDITION_REQUIRED;
        }
    }

    record AlreadyLoggedIn(String status) implements SsoJwtLoginResponse {
        public AlreadyLoggedIn() {
            this("already_logged_in");
        }
    }
}
