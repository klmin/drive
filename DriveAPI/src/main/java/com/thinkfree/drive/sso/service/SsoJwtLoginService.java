package com.thinkfree.drive.sso.service;

import com.nimbusds.jwt.JWTParser;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.auditlog.lookup.user.UserAuditLookupService;
import com.thinkfree.drive.auditlog.service.LoginAuditService;
import com.thinkfree.drive.auth.result.LoginTokenResult;
import com.thinkfree.drive.auth.service.AuthService;
import com.thinkfree.drive.auth.utils.JwtProvider;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantGuard;
import com.thinkfree.drive.mfa.result.MfaSetupChallengeResult;
import com.thinkfree.drive.mfa.service.MfaService;
import com.thinkfree.drive.sso.converter.SsoConverter;
import com.thinkfree.drive.sso.exception.SsoErrorCode;
import com.thinkfree.drive.sso.identity.converter.UserSsoIdentityConverter;
import com.thinkfree.drive.sso.identity.data.UserSsoIdentityData;
import com.thinkfree.drive.sso.identity.service.UserSsoIdentityFindService;
import com.thinkfree.drive.sso.identity.service.UserSsoIdentityService;
import com.thinkfree.drive.sso.protocol.jwt.data.SsoJwtConfigData;
import com.thinkfree.drive.sso.protocol.jwt.service.SsoJwtConfigFindService;
import com.thinkfree.drive.sso.provider.data.SsoProviderData;
import com.thinkfree.drive.sso.provider.enums.SsoProviderScope;
import com.thinkfree.drive.sso.provider.enums.SsoProviderStatus;
import com.thinkfree.drive.sso.provider.service.SsoProviderFindService;
import com.thinkfree.drive.sso.result.SsoJwtLoginResult;
import com.thinkfree.drive.superadmin.loginsetting.service.SuperAdminLoginSettingFindService;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.user.data.UserAuthData;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.MfaStatus;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.user.enums.RoleType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SsoJwtLoginService {

    private final SsoJwtConfigFindService ssoJwtConfigFindService;
    private final SsoProviderFindService ssoProviderFindService;
    private final UserFindService userFindService;
    private final AuthService authService;
    private final JwtProvider jwtProvider;
    private final MfaService mfaService;
    private final SystemConfigFindService systemConfigFindService;
    private final UserSsoIdentityFindService userSsoIdentityFindService;
    private final UserSsoIdentityService userSsoIdentityService;
    private final UserSsoIdentityConverter userSsoIdentityConverter;
    private final SsoConverter ssoConverter;
    private final SuperAdminLoginSettingFindService superAdminLoginSettingFindService;
    private final TenantPolicy tenantPolicy;
    private final UserAuditLookupService userAuditLookupService;
    private final LoginAuditService loginAuditService;
    
    public SsoJwtLoginResult login(String externalToken, String driveAccessToken, int tenantSeq, HttpServletRequest httpRequest) {

        String issuer = extractIssuerUnsafe(externalToken);
        
        var superSetting = superAdminLoginSettingFindService.find();
        // SYSTEM_MANAGED 는 전체 적용을 무시한다(로그인 설정이 막혀 테넌트 JWT 공급자가 없으므로 결과는 JWT_CONFIG_NOT_FOUND)
        boolean enforced = superSetting != null && superSetting.isEnforced() && tenantPolicy.allows(tenantSeq, TenantFeature.LOGIN_SETTING);
        SsoProviderScope expectedScope = enforced ? SsoProviderScope.GLOBAL : SsoProviderScope.TENANT;

        SsoJwtConfigData config = ssoJwtConfigFindService.findByIssuerAndScope(issuer, expectedScope);
        
        if (config == null) {
            log.error("[JWT SSO] issuer 에 해당하는 JWT 설정 없음. issuer: {}, expectedScope: {}", issuer, expectedScope);
            throw ApiRuntimeException.type(SsoErrorCode.JWT_CONFIG_NOT_FOUND);
        }

        SsoProviderData provider = ssoProviderFindService.findById(config.getSsoProviderSeq());
        if (SsoProviderStatus.ACTIVE != provider.getStatus()) {
            log.error("[JWT SSO] 비활성화된 SSO Provider. ssoProviderSeq: {}", config.getSsoProviderSeq());
            throw ApiRuntimeException.type(SsoErrorCode.PROVIDER_DISABLED);
        }
        
        if(!enforced){
            TenantGuard.verifyOwnership(provider.getTenantSeq(), SsoErrorCode.JWT_CONFIG_NOT_FOUND);
        }

        Jwt jwt = verifyToken(externalToken, config);

        if (StringUtils.hasText(config.getAudience())) {
            List<String> audience = jwt.getAudience();
            if (audience == null || !audience.contains(config.getAudience())) {
                log.error("[JWT SSO] audience 불일치. expected: {}", config.getAudience());
                throw ApiRuntimeException.type(SsoErrorCode.JWT_INVALID_TOKEN);
            }
        }

        String emailClaimName = config.getEmailClaimName();
        String email = StringUtils.hasText(emailClaimName) ? jwt.getClaimAsString(emailClaimName) : jwt.getClaimAsString(DriveConstants.EMAIL);

        if (!StringUtils.hasText(email)) {
            log.error("[JWT SSO] email claim 없음. ssoProviderSeq: {}", config.getSsoProviderSeq());
            throw ApiRuntimeException.type(SsoErrorCode.JWT_INVALID_TOKEN);
        }

        String subject = jwt.getSubject();
        
        if (!StringUtils.hasText(subject)) {
            log.error("[JWT SSO] subject claim 없음. ssoProviderSeq: {}", config.getSsoProviderSeq());
            throw ApiRuntimeException.type(SsoErrorCode.JWT_INVALID_TOKEN);
        }

        UserAuthData user = resolveUser(config, subject, email, tenantSeq, jwt);
        
        var driveClaims = jwtProvider.parseIfAccessToken(driveAccessToken);
        
        if (driveClaims.isPresent()) {
            if (driveClaims.get().userSeq() != user.getUserSeq()) {
                log.error("[JWT SSO] Drive 로그인 유저와 외부 JWT 유저 불일치. driveUserSeq: {}, externalEmail: {}", driveClaims.get().userSeq(), email);
                loginAuditService.recordLoginFailure(user);
                throw ApiRuntimeException.type(SsoErrorCode.JWT_USER_CONFLICT);
            }
            
            log.info("[JWT SSO] 동일 유저 이미 로그인. ssoProviderSeq: {}, email: {}", config.getSsoProviderSeq(), email);
            return new SsoJwtLoginResult.AlreadyLoggedIn();
        }
        
        boolean tenantMfaEnabled = systemConfigFindService.findMfaStatusConfig(user.getTenantSeq()).isEnabled();
        if (tenantMfaEnabled) {
            if (MfaStatus.ENABLED == user.getMfaStatus()) {
                log.info("[JWT SSO] MFA 필요. ssoProviderSeq: {}, email: {}", config.getSsoProviderSeq(), email);
                var result = mfaService.issueMfaChallenge(user.getUserSeq(), user.getMfaMethod());
                return ssoConverter.toJwtMfaResult(result.challengeToken(), result.expiresIn(), result.challengeType());
            }
            log.info("[JWT SSO] MFA 설정 필요. ssoProviderSeq: {}, email: {}", config.getSsoProviderSeq(), email);
            MfaSetupChallengeResult result = mfaService.issueMfaSetupChallenge(user.getUserSeq());
            return ssoConverter.toJwtMfaSetupResult(result.setupToken(), result.expiresIn());
        }

        log.info("[JWT SSO] 로그인 완료. ssoProviderSeq: {}, email: {}", config.getSsoProviderSeq(), email);
        LoginTokenResult tokenResult = authService.issueLoginTokensAndLog(user, RoleType.USER, httpRequest);
        return ssoConverter.toJwtLoginResult(user, authService.heldRoles(user.getUserSeq()), tokenResult.accessToken(), tokenResult.refreshToken());
    }

    private UserAuthData resolveUser(SsoJwtConfigData config, String subject, String email, int tenantSeq, Jwt jwt) {
        var identityData = userSsoIdentityFindService.findWithUser(config.getSsoProviderSeq(), tenantSeq, subject);

        if (identityData != null) {
            validateIdentityUser(identityData, tenantSeq);
            userSsoIdentityService.updateLastCallbackAt(identityData.getSsoIdentitySeq(), Instant.now());
            return userFindService.findAuthByUserSeq(identityData.getUserSeq());
        }

        UserAuthData user = userFindService.findActiveAuthInTenant(email, tenantSeq);
        String name = jwt.getClaimAsString(DriveConstants.NAME);
        var dto = userSsoIdentityConverter.toIdentitySaveDto(user.getUserSeq(), tenantSeq, config.getSsoProviderSeq(), subject, email, name);
        userSsoIdentityService.save(dto);
        log.info("[JWT SSO] 최초 SSO 연결 완료. ssoProviderSeq: {}, email: {}", config.getSsoProviderSeq(), email);

        return user;
    }

    private String extractIssuerUnsafe(String token) {
        try {
            return JWTParser.parse(token).getJWTClaimsSet().getIssuer();
        } catch (Exception e) {
            log.error("[JWT SSO] issuer 파싱 실패.", e);
            throw ApiRuntimeException.type(SsoErrorCode.JWT_INVALID_TOKEN);
        }
    }

    private Jwt verifyToken(String token, SsoJwtConfigData config) {
        JwtDecoder decoder = buildDecoder(config);
        try {
            return decoder.decode(token);
        } catch (Exception e) {
            log.error("[JWT SSO] 토큰 검증 실패. ssoProviderSeq: {}", config.getSsoProviderSeq(), e);
            throw ApiRuntimeException.type(SsoErrorCode.JWT_INVALID_TOKEN);
        }
    }

    private JwtDecoder buildDecoder(SsoJwtConfigData config) {
        return switch (config.getKeySourceType()) {
            case PUBLIC_KEY -> buildPublicKeyDecoder(config.getPublicKey());
            case JWKS -> NimbusJwtDecoder.withJwkSetUri(config.getJwksUri()).build();
        };
    }

    private JwtDecoder buildPublicKeyDecoder(String publicKeyPem) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyPem);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            RSAPublicKey rsaPublicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
            return NimbusJwtDecoder.withPublicKey(rsaPublicKey).build();
        } catch (Exception e) {
            log.error("[JWT SSO] RSA Public Key 파싱 실패.", e);
            throw ApiRuntimeException.type(SsoErrorCode.INTERNAL_ERROR);
        }
    }
    
    private void validateIdentityUser(UserSsoIdentityData data, int tenantSeq) {
        if (data.getUserStatus() == UserStatus.INACTIVE) {
            recordLoginFailed(data.getUserSeq());
            throw ApiRuntimeException.type(SsoErrorCode.ACCOUNT_INACTIVE);
        }
        if (data.getUserStatus() == UserStatus.PENDING_DELETE) {
            recordLoginFailed(data.getUserSeq());
            throw ApiRuntimeException.type(SsoErrorCode.ACCOUNT_PENDING_DELETE);
        }
        if(tenantSeq != data.getTenantSeq()){
            recordLoginFailed(data.getUserSeq());
            throw ApiRuntimeException.type(SsoErrorCode.JWT_USER_NOT_FOUND);
        }
    }

    // 로그인 시도 대상 계정이 DB에 실제로 존재하는 게 확인된 경우(UserSsoIdentityData엔 userSeq만 있고
    // userId/userName이 없어서 actor를 채우려면 조회가 한 번 더 필요)에만 LOGIN_FAILED를 기록한다.
    private void recordLoginFailed(Integer userSeq) {
        UserDetailData user = userAuditLookupService.findUser(userSeq);
        loginAuditService.recordLoginFailure(user);
    }

}
