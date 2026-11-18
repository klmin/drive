package com.thinkfree.drive.api.v1.auth.usecase;

import com.thinkfree.drive.api.v1.auth.request.LoginRequest;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.api.v1.auth.response.AnonymousLoginResponse;
import com.thinkfree.drive.api.v1.auth.response.AuthLoginResponse;
import com.thinkfree.drive.api.v1.auth.response.LoginConfigResponse;
import com.thinkfree.drive.api.v1.auth.response.LoginResponse;
import com.thinkfree.drive.auth.dto.LoginDto;
import com.thinkfree.drive.auth.service.AuthService;
import com.thinkfree.drive.auth.service.LoginConfigService;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthUseCase {
    
    private final AuthService authService;
    private final LoginConfigService loginConfigService;
    private final TenantPolicy tenantPolicy;
    
    @AuditLog
    public AuthLoginResponse login(LoginRequest request, HttpServletRequest httpRequest){
        return AuthLoginResponse.from(authService.login(toLoginDto(request), TenantContext.getOrThrow(), httpRequest));
    }

    @AuditLog
    public LoginResponse adminLogin(LoginRequest request, HttpServletRequest httpRequest){
        return LoginResponse.from(authService.adminLogin(toLoginDto(request), TenantContext.getOrThrow(), httpRequest));
    }

    @AuditLog
    public LoginResponse superAdminLogin(LoginRequest request, HttpServletRequest httpRequest){
        return LoginResponse.from(authService.superAdminLogin(toLoginDto(request), httpRequest));
    }

    public AnonymousLoginResponse anonymousLogin(String linkToken) {
        return AnonymousLoginResponse.from(authService.anonymousLogin(linkToken));
    }
    
    public LoginResponse loginWithEntryKey(String entryKey, HttpServletRequest httpRequest) {
        return LoginResponse.from(authService.loginWithEntryKey(entryKey, httpRequest));
    }

    public void logout(String accessToken, HttpServletRequest httpRequest){
        authService.logout(accessToken, httpRequest);
    }

    public LoginConfigResponse loginConfig() {
        int tenantSeq = TenantContext.getOrThrow();
        var result = loginConfigService.getLoginConfig(tenantSeq);
        return new LoginConfigResponse(result.loginType(), result.ssoProviderSeq(), result.errorRedirectUrl(),
                tenantPolicy.allows(tenantSeq, TenantFeature.MAIL));
    }

    /** 웹 요청 → 로그인 service 입력 */
    private static LoginDto toLoginDto(LoginRequest request) {
        return new LoginDto(request.getUserId(), request.getEncPwd());
    }
    
    
}
