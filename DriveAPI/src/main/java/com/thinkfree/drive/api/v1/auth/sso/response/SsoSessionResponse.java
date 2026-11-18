package com.thinkfree.drive.api.v1.auth.sso.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.thinkfree.drive.global.annotation.DriveDateTimeFormat;
import com.thinkfree.drive.sso.enums.SsoSessionStatus;
import com.thinkfree.drive.user.enums.DefaultLandingPage;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserLanguage;

import java.util.List;

import java.time.Instant;

public sealed interface SsoSessionResponse
        permits SsoSessionResponse.Login, SsoSessionResponse.Mfa, SsoSessionResponse.MfaSetup, SsoSessionResponse.Signup, SsoSessionResponse.Error {
    
    @JsonProperty("status")
    SsoSessionStatus status();
    
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
        implements SsoSessionResponse
    {
        @Override
        public SsoSessionStatus status() {
            return SsoSessionStatus.LOGIN;
        }
    }
    
    record Mfa
    (
        String token,
        Integer expiresIn,
        @DriveDateTimeFormat
        Instant expiresAt,
        String challengeType
    )
        implements SsoSessionResponse
    {
        @Override
        public SsoSessionStatus status() {
            return SsoSessionStatus.MFA_VERIFY;
        }
    }
    
    record MfaSetup
    (
        String token,
        Integer expiresIn,
        @DriveDateTimeFormat
        Instant expiresAt
    )
        implements SsoSessionResponse
    {
        @Override
        public SsoSessionStatus status() {
            return SsoSessionStatus.MFA_SETUP;
        }
    }

    record Signup
    (
        String email,
        String name,
        String signupToken
    )
        implements SsoSessionResponse
    {
        @Override
        public SsoSessionStatus status() {
            return SsoSessionStatus.SIGN_UP;
        }
    }
    
    record Error
    (
        String errorCode,
        Integer errorStatus
    )
        implements SsoSessionResponse
    {
        @Override
        public SsoSessionStatus status() {
            return SsoSessionStatus.ERROR;
        }
    }
    
}
