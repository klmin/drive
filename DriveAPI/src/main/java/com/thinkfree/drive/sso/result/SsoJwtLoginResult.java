package com.thinkfree.drive.sso.result;

import com.thinkfree.drive.mfa.enums.MfaChallengeType;
import com.thinkfree.drive.user.enums.DefaultLandingPage;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserLanguage;

import java.util.List;

import java.time.Instant;

public sealed interface SsoJwtLoginResult
        permits SsoJwtLoginResult.Login, SsoJwtLoginResult.Mfa, SsoJwtLoginResult.MfaSetup, SsoJwtLoginResult.AlreadyLoggedIn {

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
        String accessToken,
        String refreshToken
    )
        implements SsoJwtLoginResult
    {}

    record Mfa
    (
        MfaChallengeType mfaChallengeType,
        String token,
        Integer expiresIn,
        Instant expiresAt,
        String challengeType
    )
        implements SsoJwtLoginResult
    {}

    record MfaSetup
    (
        MfaChallengeType mfaChallengeType,
        String token,
        Integer expiresIn,
        Instant expiresAt
    )
        implements SsoJwtLoginResult
    {}

    record AlreadyLoggedIn() implements SsoJwtLoginResult {}
}
