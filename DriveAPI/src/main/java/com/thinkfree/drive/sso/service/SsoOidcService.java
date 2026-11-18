package com.thinkfree.drive.sso.service;

import com.thinkfree.drive.auditlog.lookup.user.UserAuditLookupService;
import com.thinkfree.drive.auditlog.service.LoginAuditService;
import com.thinkfree.drive.auth.enums.JwtType;
import com.thinkfree.drive.auth.model.SsoStateClaims;
import com.thinkfree.drive.auth.result.LoginTokenResult;
import com.thinkfree.drive.auth.service.AuthService;
import com.thinkfree.drive.auth.utils.JwtProvider;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.global.security.TenantGuard;
import com.thinkfree.drive.global.service.ClientDomainService;
import com.thinkfree.drive.mfa.result.MfaChallengeResult;
import com.thinkfree.drive.mfa.result.MfaSetupChallengeResult;
import com.thinkfree.drive.mfa.service.MfaService;
import com.thinkfree.drive.sso.converter.SsoConverter;
import com.thinkfree.drive.sso.exception.SsoErrorCode;
import com.thinkfree.drive.sso.handler.SsoAuthHandler;
import com.thinkfree.drive.sso.handler.params.OidcCallbackParams;
import com.thinkfree.drive.sso.handler.params.SsoCallbackParams;
import com.thinkfree.drive.sso.identity.data.UserSsoIdentityData;
import com.thinkfree.drive.sso.identity.dto.UserSsoIdentitySaveDto;
import com.thinkfree.drive.sso.identity.service.UserSsoIdentityFindService;
import com.thinkfree.drive.sso.identity.service.UserSsoIdentityService;
import com.thinkfree.drive.sso.model.SsoUserInfo;
import com.thinkfree.drive.sso.properties.SsoProperties;
import com.thinkfree.drive.sso.protocol.data.SsoProtocolData;
import com.thinkfree.drive.sso.provider.data.SsoProviderData;
import com.thinkfree.drive.sso.provider.data.SsoProviderDetailData;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import com.thinkfree.drive.sso.provider.enums.SsoProviderStatus;
import com.thinkfree.drive.sso.provider.service.SsoProviderFindService;
import com.thinkfree.drive.sso.registry.SsoHandlerRegistry;
import com.thinkfree.drive.sso.result.SsoCallbackResult;
import com.thinkfree.drive.sso.result.SsoSessionTokenResult;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.user.data.UserAuthData;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.MfaStatus;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.util.StringUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class SsoOidcService implements SsoService {
    
    private final SsoConverter ssoConverter;
    private final SsoProviderFindService ssoProviderFindService;
    private final UserSsoIdentityFindService userSsoIdentityFindService;
    private final UserSsoIdentityService userSsoIdentityService;
    private final SsoHandlerRegistry handlerRegistry;
    private final JwtProvider jwtProvider;
    private final ClientDomainService clientDomainService;
    private final SystemConfigFindService systemConfigFindService;
    private final UserFindService userFindService;
    private final MfaService mfaService;
    private final SsoProperties ssoProperties;
    private final AuthService authService;
    private final UserAuditLookupService userAuditLookupService;
    private final LoginAuditService loginAuditService;

    @Override
    public SsoProviderProtocolType supports() {
        return SsoProviderProtocolType.OIDC;
    }
    
    @Override
    public String buildAuthorizationUrl(SsoProviderDetailData detail) {

        SsoProviderData ssoProviderData = detail.getSsoProviderData();
        SsoProtocolData ssoProtocolData = detail.getSsoProtocolData();

        if (SsoProviderStatus.ACTIVE != ssoProviderData.getStatus()) {
            throw ApiRuntimeException.type(SsoErrorCode.PROVIDER_DISABLED);
        }

        int ssoProviderSeq = ssoProviderData.getSsoProviderSeq();
        SsoAuthHandler handler = handlerRegistry.get(ssoProviderData.getProtocolType());
        String callbackUrl = buildCallBackUrl(handler.callbackPath());
        int tenantSeqForState = ssoProviderData.getScope() == SsoProviderScope.GLOBAL
                ? TenantContext.getOrThrow()
                : ssoProviderData.getTenantSeq();
        String state = jwtProvider.createSsoStateToken(ssoProviderSeq, tenantSeqForState);

        log.info("[SSO] buildAuthorizationUrl. providerSeq : {}, callbackUrl : {}", ssoProviderSeq, callbackUrl);
        return handler.buildAuthorizationUrl(ssoProtocolData, state, callbackUrl);
    }

    @Override
    @Transactional
    public SsoCallbackResult handleCallback(String code, String state) {

        SsoStateClaims stateClaims = validateState(state);
        Integer ssoProviderSeq = stateClaims.ssoProviderSeq();
        Integer tenantSeq = stateClaims.tenantSeq();
       
        TenantGuard.verifyOwnership(tenantSeq, SsoErrorCode.INVALID_STATE);

        SsoProviderDetailData detail = ssoProviderFindService.findDetailById(ssoProviderSeq);
        SsoProviderData provider = detail.getSsoProviderData();
        SsoProtocolData config = detail.getSsoProtocolData();
        SsoAuthHandler handler = handlerRegistry.get(provider.getProtocolType());
        String callbackUrl = buildCallBackUrl(handler.callbackPath());

        SsoCallbackParams params = new OidcCallbackParams(code, callbackUrl);
        SsoUserInfo userInfo = handler.handleCallback(provider, config, params);

        log.info("[SSO] callback 수신. providerSeq : {}, tenantSeq : {}, subject : {}, email : {}, name : {}", ssoProviderSeq, provider.getTenantSeq(), userInfo.subject()
        , userInfo.email(), userInfo.name());

        UserSsoIdentityData userSsoIdentityData = userSsoIdentityFindService.findWithUser(ssoProviderSeq, tenantSeq, userInfo.subject());

        if (userSsoIdentityData != null) {
            var token = buildTokenForExistingUser(userSsoIdentityData, tenantSeq);
            return new SsoCallbackResult(token);
        }
        
        var userId = userInfo.email();

        if (StringUtils.isBlank(userId)) {
            throw ApiRuntimeException.type(SsoErrorCode.OIDC_USER_NOT_FOUND);
        }

        var user = userFindService.findActiveAuthInTenant(userId, tenantSeq);
        
        var token = buildTokenForExistingUser(user, userInfo, ssoProviderSeq, tenantSeq);
        return new SsoCallbackResult(token);
        
    }

    private String buildTokenForExistingUser(UserSsoIdentityData data, int tenantSeq) {
        var ssoIdentitySeq = data.getSsoIdentitySeq();
        var userSeq = data.getUserSeq();

        if (data.getUserStatus() == UserStatus.INACTIVE) {
            log.error("[SSO] 비활성화 계정. ssoIdentitySeq : {}, userSeq : {}", ssoIdentitySeq, userSeq);
            recordLoginFailed(userSeq);
            throw ApiRuntimeException.type(SsoErrorCode.ACCOUNT_INACTIVE);
        }
        if (data.getUserStatus() == UserStatus.PENDING_DELETE) {
            log.error("[SSO] 삭제 대기 계정. ssoIdentitySeq : {}, userSeq : {}", ssoIdentitySeq, userSeq);
            recordLoginFailed(userSeq);
            throw ApiRuntimeException.type(SsoErrorCode.ACCOUNT_PENDING_DELETE);
        }
        if(tenantSeq != data.getTenantSeq()){
            log.error("[SSO] 테넌트 불일치. ssoIdentitySeq : {}, userSeq : {}, tenantSeq : {}, identityTenantSeq : {}",
                    ssoIdentitySeq, userSeq, tenantSeq, data.getTenantSeq());
            recordLoginFailed(userSeq);
            throw ApiRuntimeException.type(SsoErrorCode.OIDC_USER_NOT_FOUND);
        }
        
        userSsoIdentityService.updateLastCallbackAt(ssoIdentitySeq, Instant.now());
        boolean tenantMfaEnabled = systemConfigFindService.findMfaStatusConfig(tenantSeq).isEnabled();
        if (tenantMfaEnabled) {
            if (MfaStatus.ENABLED == data.getMfaStatus()) {
                log.info("[SSO] MFA 필요. ssoIdentitySeq : {}, userSeq : {}", ssoIdentitySeq, userSeq);
                return jwtProvider.createSsoMfaToken(userSeq, tenantSeq, SsoProviderProtocolType.OIDC);
            }
            log.info("[SSO] MFA 설정 필요. ssoIdentitySeq : {}, userSeq : {}", ssoIdentitySeq, userSeq);
            return jwtProvider.createSsoMfaSetupToken(userSeq, tenantSeq, SsoProviderProtocolType.OIDC);
        }

        log.info("[SSO] 기존 SSO 유저 로그인 확인. ssoIdentitySeq : {}, userSeq : {}", ssoIdentitySeq, userSeq);
        return jwtProvider.createSsoLoginToken(userSeq, tenantSeq, SsoProviderProtocolType.OIDC);
    }

    private String buildTokenForExistingUser(UserAuthData user, SsoUserInfo userInfo, int ssoProviderSeq, int tenantSeq) {
        var userSeq = user.getUserSeq();

        var saveDto = UserSsoIdentitySaveDto.builder()
                .userSeq(userSeq)
                .tenantSeq(tenantSeq)
                .ssoProviderSeq(ssoProviderSeq)
                .subject(userInfo.subject())
                .email(userInfo.email())
                .name(userInfo.name())
                .build();
        userSsoIdentityService.save(saveDto);
        log.info("[SSO] 최초 SSO 연결 완료. userSeq : {}, ssoProviderSeq : {}", userSeq, ssoProviderSeq);

        boolean tenantMfaEnabled = systemConfigFindService.findMfaStatusConfig(tenantSeq).isEnabled();
        if (tenantMfaEnabled) {
            if (MfaStatus.ENABLED == user.getMfaStatus()) {
                log.info("[SSO] MFA 필요. userSeq : {}", userSeq);
                return jwtProvider.createSsoMfaToken(userSeq, tenantSeq, SsoProviderProtocolType.OIDC);
            }
            log.info("[SSO] MFA 설정 필요. userSeq : {}", userSeq);
            return jwtProvider.createSsoMfaSetupToken(userSeq, tenantSeq, SsoProviderProtocolType.OIDC);
        }

        log.info("[SSO] 기존 일반 유저 SSO 연결 후 로그인. userSeq : {}", userSeq);
        return jwtProvider.createSsoLoginToken(userSeq, tenantSeq, SsoProviderProtocolType.OIDC);
    }
    
    @Override
    public SsoSessionTokenResult resolveSessionToken(Claims claims, HttpServletRequest httpRequest) {
        
        String type;
        
        try {
            type = claims.get(JwtProvider.CLAIMS_TYPE, String.class);
        }catch(JwtException e){
            throw ApiRuntimeException.type(SsoErrorCode.INVALID_SSO_SESSION_TOKEN);
        }
        
        JwtType jwtType = JwtType.findByName(type);
        
        return switch (jwtType){
            case SSO_LOGIN -> {
                var ssoSessionContext = resolveVerifiedContext(claims);
                UserAuthData user;
                try {
                    user = userFindService.findActiveAuthInTenant(ssoSessionContext.userSeq, ssoSessionContext.tenantSeq);
                } catch (RuntimeException e) {
                    recordLoginFailed(ssoSessionContext.userSeq);
                    throw e;
                }
                LoginTokenResult loginTokenResult = authService.issueLoginTokensAndLog(user, RoleType.USER, httpRequest);
                yield ssoConverter.toSessionTokenLoginResult(user, authService.heldRoles(user.getUserSeq()), loginTokenResult.accessToken(), loginTokenResult.refreshToken());
            }
            case SSO_MFA_VERIFY -> {
                var ssoSessionContext = resolveVerifiedContext(claims);
                UserAuthData user = userFindService.findActiveAuthInTenant(ssoSessionContext.userSeq, ssoSessionContext.tenantSeq);
                MfaChallengeResult result = mfaService.issueMfaChallenge(user.getUserSeq(), user.getMfaMethod());
                yield ssoConverter.toSessionTokenMfaResult(result.challengeToken(), result.expiresIn(), result.challengeType());
            }
            case SSO_MFA_SETUP -> {
                var ssoSessionContext = resolveVerifiedContext(claims);
                MfaSetupChallengeResult result = mfaService.issueMfaSetupChallenge(ssoSessionContext.userSeq);
                yield ssoConverter.toSessionTokenMfaSetupResult(result.setupToken(), result.expiresIn());
            }
            case SSO_ERROR -> {
                String errorCode = claims.get(JwtProvider.CLAIMS_ERROR_CODE, String.class);
                int errorStatus = claims.get(JwtProvider.CLAIMS_ERROR_STATUS, Integer.class);
                yield ssoConverter.toSessionTokenErrorResult(errorCode, errorStatus);
            }
            default -> throw ApiRuntimeException.type(SsoErrorCode.INVALID_SSO_SESSION_TOKEN);
        };
    }
    
    private record SsoSessionContext(int userSeq, int tenantSeq) {}

    private SsoSessionContext resolveVerifiedContext(Claims claims) {
        Integer userSeq = claims.get(JwtProvider.CLAIMS_USER_SEQ, Integer.class);
        Integer tenantSeq = claims.get(JwtProvider.CLAIMS_TENANT_SEQ, Integer.class);
        try {
            TenantGuard.verifyOwnership(tenantSeq, SsoErrorCode.INVALID_SSO_SESSION_TOKEN);
        } catch (RuntimeException e) {
            recordLoginFailed(userSeq);
            throw e;
        }
        return new SsoSessionContext(userSeq, tenantSeq);
    }

    // 토큰(claims)에서 이미 뽑은 userSeq로 실제 존재하는 계정인지 조회해서 actor를 채운다.
    // findByUserSeq 자체가 실패하면(계정 완전삭제 등) actor를 못 채우니 이 경우엔 기록하지 않는다.
    private void recordLoginFailed(Integer userSeq) {
        try {
            UserDetailData user = userAuditLookupService.findUser(userSeq);
            loginAuditService.recordLoginFailure(user);
        } catch (RuntimeException e) {
            log.warn("[SSO] LOGIN_FAILED 기록용 유저 조회 실패. userSeq : {}", userSeq, e);
        }
    }
    
    
    private SsoStateClaims validateState(String state) {
        try {
            return jwtProvider.validateSsoState(state);
        } catch (Exception e) {
            log.warn("[SSO] state 검증 실패: {}", e.getMessage());
            throw ApiRuntimeException.type(SsoErrorCode.INVALID_STATE);
        }
    }

    private String buildCallBackUrl(String callbackPath) {
        return clientDomainService.getDomainBackEnd(TenantContext.getOrThrow()) + callbackPath;
    }
    
    @Override
    public String redirectUrl() {
        return StringUtil.buildUrl(clientDomainService.getDomain(TenantContext.getOrThrow()), ssoProperties.frontendCallbackPath());
    }
    
}
