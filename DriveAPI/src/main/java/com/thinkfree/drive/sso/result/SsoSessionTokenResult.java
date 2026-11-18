package com.thinkfree.drive.sso.result;

import com.thinkfree.drive.mfa.enums.MfaChallengeType;
import com.thinkfree.drive.user.enums.DefaultLandingPage;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserLanguage;

import java.util.List;

import java.time.Instant;

public sealed interface SsoSessionTokenResult
    permits SsoSessionTokenResult.Login,
            SsoSessionTokenResult.Mfa,
            SsoSessionTokenResult.MfaSetup,
            SsoSessionTokenResult.Signup,
            SsoSessionTokenResult.Error {
    
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
        implements SsoSessionTokenResult
    {}
    
    record Mfa
    (
        MfaChallengeType mfaChallengeType,
        String token,
        Integer expiresIn,
        Instant expiresAt,
        String challengeType
    )
        implements SsoSessionTokenResult
    {}
    
    record MfaSetup
    (
        MfaChallengeType mfaChallengeType,
        String token,
        Integer expiresIn,
        Instant expiresAt
    )
        implements SsoSessionTokenResult
    {}

    record Signup
    (
        String email,
        String name,
        String signupToken
    )
        implements SsoSessionTokenResult
    {}
    
    record Error
    (
        String errorCode,
        Integer errorStatus
    )
        implements SsoSessionTokenResult
    {}
}
