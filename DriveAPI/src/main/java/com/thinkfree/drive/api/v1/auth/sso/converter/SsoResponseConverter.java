package com.thinkfree.drive.api.v1.auth.sso.converter;

import com.thinkfree.drive.api.v1.auth.sso.response.SsoJwtLoginResponse;
import com.thinkfree.drive.api.v1.auth.sso.response.SsoSessionResponse;
import com.thinkfree.drive.api.v1.auth.sso.response.SsoSignupResponse;
import com.thinkfree.drive.signup.result.SignupResult;
import com.thinkfree.drive.sso.result.SsoJwtLoginResult;
import com.thinkfree.drive.sso.result.SsoSessionTokenResult;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SsoResponseConverter {
    
    default SsoSessionResponse toSsoSessionResponse(SsoSessionTokenResult result){
        if (result instanceof SsoSessionTokenResult.Login login) {
            return toSsoSessionLoginResponse(login);
        }
        if (result instanceof SsoSessionTokenResult.Mfa mfa) {
            return toSsoSessionMfaResponse(mfa);
        }
        if (result instanceof SsoSessionTokenResult.MfaSetup mfaSetup) {
            return toSsoSessionMfaSetupResponse(mfaSetup);
        }
        if (result instanceof SsoSessionTokenResult.Signup signup) {
            return toSsoSessionSignupResponse(signup);
        }
        if (result instanceof SsoSessionTokenResult.Error error) {
            return toSsoSessionErrorResponse(error);
        }
        throw new IllegalArgumentException("Unsupported result type: " + result.getClass());
    };
    
    SsoSessionResponse.Login toSsoSessionLoginResponse(SsoSessionTokenResult.Login login);
    SsoSessionResponse.Mfa toSsoSessionMfaResponse(SsoSessionTokenResult.Mfa mfa);
    
    SsoSessionResponse.MfaSetup toSsoSessionMfaSetupResponse(SsoSessionTokenResult.MfaSetup mfaSetup);

    SsoSessionResponse.Signup toSsoSessionSignupResponse(SsoSessionTokenResult.Signup signup);
    SsoSessionResponse.Error toSsoSessionErrorResponse(SsoSessionTokenResult.Error error);
    
    SsoSignupResponse toSsoSignupResponse(SignupResult result);

    default SsoJwtLoginResponse toSsoJwtLoginResponse(SsoJwtLoginResult result) {
        if (result instanceof SsoJwtLoginResult.Login login) {
            return toSsoJwtLoginLoginResponse(login);
        }
        if (result instanceof SsoJwtLoginResult.Mfa mfa) {
            return toSsoJwtLoginMfaResponse(mfa);
        }
        if (result instanceof SsoJwtLoginResult.MfaSetup mfaSetup) {
            return toSsoJwtLoginMfaSetupResponse(mfaSetup);
        }
        if (result instanceof SsoJwtLoginResult.AlreadyLoggedIn) {
            return new SsoJwtLoginResponse.AlreadyLoggedIn();
        }
        throw new IllegalArgumentException("Unsupported result type: " + result.getClass());
    }

    SsoJwtLoginResponse.Login toSsoJwtLoginLoginResponse(SsoJwtLoginResult.Login login);
    SsoJwtLoginResponse.Mfa toSsoJwtLoginMfaResponse(SsoJwtLoginResult.Mfa mfa);
    
    SsoJwtLoginResponse.MfaSetup toSsoJwtLoginMfaSetupResponse(SsoJwtLoginResult.MfaSetup mfaSetup);
}
