package com.thinkfree.drive.api.v1.auth.mfa.usecase;

import com.thinkfree.drive.api.v1.auth.mfa.request.MfaConfirmRequest;
import com.thinkfree.drive.api.v1.auth.mfa.request.MfaSetupRequest;
import com.thinkfree.drive.api.v1.auth.mfa.response.MfaSetupResponse;
import com.thinkfree.drive.api.v1.auth.response.LoginResponse;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthMfaUseCase {

    private final AuthService authService;

    public MfaSetupResponse setup(MfaSetupRequest request) {
        return MfaSetupResponse.from(authService.setupMfaForLogin(request.t(), request.m()));
    }

    @AuditLog
    public LoginResponse confirm(MfaConfirmRequest request, HttpServletRequest httpRequest) {
        return LoginResponse.from(authService.confirmMfaForLogin(request.t(), request.c(), request.m(), httpRequest));
    }

    @AuditLog
    public LoginResponse verify(String challengeToken, String code, HttpServletRequest httpRequest){
        return LoginResponse.from(authService.verifyMfaLogin(challengeToken, code, httpRequest));
    }
}
