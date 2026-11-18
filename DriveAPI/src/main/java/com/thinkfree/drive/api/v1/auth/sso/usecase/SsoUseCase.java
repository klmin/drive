package com.thinkfree.drive.api.v1.auth.sso.usecase;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.api.v1.auth.sso.converter.SsoRequestConverter;
import com.thinkfree.drive.api.v1.auth.sso.converter.SsoResponseConverter;
import com.thinkfree.drive.api.v1.auth.sso.request.SsoOidcCallbackRequest;
import com.thinkfree.drive.api.v1.auth.sso.request.SsoSignupRequest;
import com.thinkfree.drive.api.v1.auth.sso.response.SsoCallbackResponse;
import com.thinkfree.drive.api.v1.auth.sso.response.SsoJwtLoginResponse;
import com.thinkfree.drive.api.v1.auth.sso.response.SsoSessionResponse;
import com.thinkfree.drive.api.v1.auth.sso.response.SsoSignupResponse;
import com.thinkfree.drive.application.quota.TenantUserQuotaGuard;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auth.model.SsoSignupClaims;
import com.thinkfree.drive.auth.service.LoginConfigService;
import com.thinkfree.drive.auth.utils.JwtProvider;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.global.security.TenantGuard;
import com.thinkfree.drive.signup.registry.SignupRegistry;
import com.thinkfree.drive.signup.service.SignupService;
import com.thinkfree.drive.sso.exception.SsoErrorCode;
import com.thinkfree.drive.sso.protocol.oidc.dto.OidcSignupDto;
import com.thinkfree.drive.sso.provider.data.SsoProviderDetailData;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import com.thinkfree.drive.sso.provider.exception.SsoProviderErrorCode;
import com.thinkfree.drive.sso.provider.service.SsoProviderFindService;
import com.thinkfree.drive.sso.registry.SsoServiceRegistry;
import com.thinkfree.drive.sso.service.SsoJwtLoginService;
import com.thinkfree.drive.sso.service.SsoService;
import com.thinkfree.drive.user.enums.SignupType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class SsoUseCase {

    private final SsoRequestConverter ssoRequestConverter;
    private final SsoResponseConverter ssoResponseConverter;
    private final SsoProviderFindService ssoProviderFindService;
    private final SsoServiceRegistry ssoServiceRegistry;
    private final SignupRegistry signupRegistry;
    private final JwtProvider jwtProvider;
    private final SsoJwtLoginService ssoJwtLoginService;
    private final LoginConfigService loginConfigService;
    private final TenantUserQuotaGuard tenantUserQuotaGuard;

    public String authorizationUrl(int ssoProviderSeq) {

        int tenantSeq = TenantContext.getOrThrow();
        loginConfigService.validateLoginType(tenantSeq, LoginType.OIDC);

        SsoProviderDetailData detail = ssoProviderFindService.findDetailById(ssoProviderSeq);
        var provider = detail.getSsoProviderData();
        
        boolean enforced = loginConfigService.isSuperAdminEnforced(tenantSeq);
        SsoProviderScope expectedScope = enforced ? SsoProviderScope.GLOBAL : SsoProviderScope.TENANT;
        if (provider.getScope() != expectedScope) {
            log.error("[SSO] scope 불일치. expected: {}, actual: {}, ssoProviderSeq: {}",
                    expectedScope, provider.getScope(), ssoProviderSeq);
            throw ApiRuntimeException.type(SsoProviderErrorCode.SSO_PROVIDER_NOT_FOUND);
        }
        
        if (provider.getScope() == SsoProviderScope.TENANT) {
            TenantGuard.verifyOwnership(provider.getTenantSeq(), SsoProviderErrorCode.SSO_PROVIDER_NOT_FOUND);
        }

        SsoProviderProtocolType protocolType = provider.getProtocolType();
        SsoService ssoService = ssoServiceRegistry.get(protocolType);
        String authorizationUrl = ssoService.buildAuthorizationUrl(detail);
        log.info("[SSO] login redirect. ssoProviderSeq : {}", ssoProviderSeq);
        return authorizationUrl;
    }

    @AuditLog
    public SsoCallbackResponse handleOidcCallback(SsoOidcCallbackRequest request) {
        log.info("code : {}, state : {}", request.code(), request.state());
        var ssoService = ssoServiceRegistry.get(SsoProviderProtocolType.OIDC);
        try {
            var result = ssoService.handleCallback(request.code(), request.state());
            return new SsoCallbackResponse(result.token(), ssoService.redirectUrl());
        } catch (ApiRuntimeException e) {
            var errorCode = e.getCode();
            log.error("[SSO] callback 오류. code : {}", errorCode.getErrorCode());
            var token = jwtProvider.createSsoErrorToken(errorCode.getErrorCode(), errorCode.getStatus().value(), SsoProviderProtocolType.OIDC);
            return new SsoCallbackResponse(token, ssoService.redirectUrl());
        }
    }

    @AuditLog
    public SsoSessionResponse resolveSession(String ssoSessionToken, HttpServletRequest httpRequest) {

        if(!StringUtils.hasText(ssoSessionToken)){
            log.info("## Required Sso Session Token ##");
            throw ApiRuntimeException.type(SsoErrorCode.REQUIRED_SSO_SESSION_TOKEN);
        }

        try {
            Claims claims = jwtProvider.getClaims(ssoSessionToken);
            String protocol = claims.get(JwtProvider.CLAIMS_PROTOCOL, String.class);
            SsoProviderProtocolType protocolType = SsoProviderProtocolType.from(protocol);
            SsoService ssoService = ssoServiceRegistry.get(protocolType);
            var result = ssoService.resolveSessionToken(claims, httpRequest);
            log.info("result : {}", result);
            return ssoResponseConverter.toSsoSessionResponse(result);
        }catch(JwtException e){
            log.error("[Invalid ssoSessionToken] token : {}", ssoSessionToken, e);
            throw ApiRuntimeException.type(SsoErrorCode.INVALID_SSO_SESSION_TOKEN);
        }

    }

    @Deprecated
    @Transactional // 테넌트 총 용량 판정의 테넌트 행 잠금을 가입(사용자 INSERT) 커밋까지 유지
    public SsoSignupResponse signup(SsoSignupRequest request, HttpServletRequest httpServletRequest){

        SsoSignupClaims ssoSignupClaims;

        try {
            ssoSignupClaims = jwtProvider.parseSsoSignupToken(request.st());
        } catch (JwtException e) {
            throw ApiRuntimeException.type(SsoErrorCode.INVALID_SIGNUP_TOKEN);
        }

        SignupType signupType = getSignupType(ssoSignupClaims.protocolType());
        SignupService signupService = signupRegistry.get(signupType);
        OidcSignupDto dto = ssoRequestConverter.toOidcSignupDto(ssoSignupClaims, request);
        // 가입 계정은 기본 할당량(1GB)으로 만들어진다 — 테넌트 총 용량 안에 들어가는지 먼저 본다
        tenantUserQuotaGuard.ensureAssignable(dto.tenantSeq(), DriveConstants.DEFAULT_USER_QUOTA_BYTE);
        var result = signupService.signup(dto, httpServletRequest);
        return ssoResponseConverter.toSsoSignupResponse(result);
    }

    @Deprecated
    private SignupType getSignupType(SsoProviderProtocolType protocolType){
        return switch (protocolType){
            case OIDC -> SignupType.SSO_OIDC;
            case SAML -> SignupType.SSO_SAML;
            default -> throw ApiRuntimeException.type(SsoErrorCode.UNSUPPORTED_PROTOCOL);
        };
    }

    @AuditLog
    public SsoJwtLoginResponse jwtLogin(String externalToken, String driveAccessToken, HttpServletRequest httpRequest) {
        int tenantSeq = TenantContext.getOrThrow();
        loginConfigService.validateLoginType(tenantSeq, LoginType.JWT);
        var result = ssoJwtLoginService.login(externalToken, driveAccessToken, tenantSeq, httpRequest);
        return ssoResponseConverter.toSsoJwtLoginResponse(result);
    }

}
