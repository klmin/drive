package com.thinkfree.drive.sso.converter;

import com.thinkfree.drive.mfa.enums.MfaChallengeType;
import com.thinkfree.drive.sso.result.SsoJwtLoginResult;
import com.thinkfree.drive.sso.result.SsoSessionTokenResult;
import com.thinkfree.drive.user.data.UserAuthData;
import com.thinkfree.drive.user.enums.RoleType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.List;

@Mapper(componentModel = "spring", imports = {Instant.class, MfaChallengeType.class})
public interface SsoConverter {
    
    /** roles = 보유 역할 전부(토큰 role 클레임은 SSO 사용자 로그인 → USER) */
    @Mapping(target = "roles", source = "roles")
    SsoSessionTokenResult.Login toSessionTokenLoginResult(UserAuthData user, List<RoleType> roles, String accessToken, String refreshToken);
    
    @Mapping(target = "expiresAt", expression = "java(java.time.Instant.now().plusSeconds(expiresIn))")
    SsoSessionTokenResult.Mfa toSessionTokenMfaResult(String token, int expiresIn, String challengeType);
    
    @Mapping(target = "expiresAt", expression = "java(java.time.Instant.now().plusSeconds(expiresIn))")
    SsoSessionTokenResult.MfaSetup toSessionTokenMfaSetupResult(String token, int expiresIn);

    SsoSessionTokenResult.Signup toSessionTokenSignupResult(String email, String name, String signupToken);
    SsoSessionTokenResult.Error toSessionTokenErrorResult(String errorCode, int errorStatus);

    /** roles = 보유 역할 전부(토큰 role 클레임은 SSO 사용자 로그인 → USER) */
    @Mapping(target = "roles", source = "roles")
    SsoJwtLoginResult.Login toJwtLoginResult(UserAuthData user, List<RoleType> roles, String accessToken, String refreshToken);

    @Mapping(target = "mfaChallengeType", expression = "java(MfaChallengeType.VERIFY)")
    @Mapping(target = "expiresAt", expression = "java(java.time.Instant.now().plusSeconds(expiresIn))")
    SsoJwtLoginResult.Mfa toJwtMfaResult(String token, int expiresIn, String challengeType);

    @Mapping(target = "mfaChallengeType", expression = "java(MfaChallengeType.SETUP)")
    @Mapping(target = "expiresAt", expression = "java(java.time.Instant.now().plusSeconds(expiresIn))")
    SsoJwtLoginResult.MfaSetup toJwtMfaSetupResult(String token, int expiresIn);
}
