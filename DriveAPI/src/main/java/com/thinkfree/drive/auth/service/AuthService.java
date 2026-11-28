package com.thinkfree.drive.auth.service;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.auth.result.AnonymousLoginResult;
import com.thinkfree.drive.basedomain.data.BaseDomainData;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.service.ShareFindService;
import com.thinkfree.drive.auth.result.AuthLoginResult;
import com.thinkfree.drive.auth.result.LoginMfaSetupResult;
import com.thinkfree.drive.auth.result.LoginResult;
import com.thinkfree.drive.auth.dto.LoginDto;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.service.LoginAuditService;
import com.thinkfree.drive.auth.enums.JwtType;
import com.thinkfree.drive.auth.exception.AuthErrorCode;
import com.thinkfree.drive.auth.result.LoginTokenResult;
import com.thinkfree.drive.auth.token.blacklist.query.BlacklistFindQuery;
import com.thinkfree.drive.auth.token.blacklist.service.BlacklistTokenFindService;
import com.thinkfree.drive.auth.token.blacklist.service.BlacklistTokenService;
import com.thinkfree.drive.auth.token.refresh.service.impl.DefaultRefreshTokenService;
import com.thinkfree.drive.auth.utils.JwtProvider;
import com.thinkfree.drive.auth.utils.TokenHasher;
import com.thinkfree.drive.basedomain.service.BaseDomainFindService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.ratelimit.LoginRateLimitGuard;
import com.thinkfree.drive.valid.DriveAssert;
import com.thinkfree.drive.global.security.TenantGuard;
import com.thinkfree.drive.mfa.result.MfaLoginSetupResult;
import com.thinkfree.drive.mfa.result.MfaSetupChallengeResult;
import com.thinkfree.drive.mfa.service.MfaService;
import com.thinkfree.drive.superadmin.token.result.TenantAccessTokenConsumeResult;
import com.thinkfree.drive.superadmin.token.service.TenantAccessTokenService;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.data.LoginAttempt;
import com.thinkfree.drive.user.data.UserAuthData;
import com.thinkfree.drive.user.util.LoginLockPolicy;
import com.thinkfree.drive.user.data.UserIdentity;
import com.thinkfree.drive.user.enums.MfaMethod;
import com.thinkfree.drive.user.enums.MfaStatus;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.role.service.UserRoleFindService;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.user.service.UserService;
import com.thinkfree.drive.auth.utils.UuidV7Generator;
import com.thinkfree.drive.userlog.service.UserLogService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static com.thinkfree.drive.global.utils.PasswordEncoderUtils.ENCODER;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final JwtProvider jwtProvider;
    private final DefaultRefreshTokenService refreshTokenService;
    private final UserFindService userFindService;
    private final UserLogService userLogService;
    private final LoginAuditService loginAuditService;
    private final UserService userService;
    private final BlacklistTokenService blacklistTokenService;
    private final BlacklistTokenFindService blacklistTokenFindService;
    private final MfaService mfaService;
    private final SystemConfigFindService systemConfigFindService;
    private final LoginConfigService loginConfigService;
    private final TenantAccessTokenService tenantAccessTokenService;
    private final BaseDomainFindService baseDomainFindService;
    private final ResourceFindService resourceFindService;
    private final ShareFindService shareFindService;
    private final UserRoleFindService userRoleFindService;
    private final TenantPolicy tenantPolicy;
    private final LoginRateLimitGuard loginRateLimitGuard;

    @Transactional
    public AuthLoginResult login(LoginDto loginDto, int tenantSeq, HttpServletRequest httpRequest) {
        log.info("{ AuthService } : 로그인");
        loginConfigService.validateLoginType(tenantSeq, LoginType.LOCAL);
        UserAuthData findUser = findUserOrThrowLoginFailed(() -> userFindService.findActiveAuthInTenant(loginDto.userId(), tenantSeq));
        verifyPassword(loginDto.encPwd(), findUser, RoleType.USER);
        log.info("{ AuthService } : 비밀번호 일치");

        boolean tenantMfaEnabled = systemConfigFindService.findMfaStatusConfig(tenantSeq).isEnabled();

        if (tenantMfaEnabled) {
            return decideMfaResult(findUser);
        }

        boolean hasChangedPassword = userLogService.hasChangedPassword(findUser.getUserSeq());
        return completeLoginSuccess(findUser, RoleType.USER, httpRequest, hasChangedPassword);
    }

    @Transactional
    public LoginResult adminLogin(LoginDto loginDto, int tenantSeq, HttpServletRequest httpRequest) {
        log.info("{ AuthService } : 관리자 로그인");
        UserAuthData findAdmin = findUserOrThrowLoginFailed(() -> userFindService.findActiveAdminAuthInTenant(loginDto.userId(), tenantSeq));

        verifyPassword(loginDto.encPwd(), findAdmin, RoleType.ADMIN);
        log.info("{ AuthService } : 비밀번호 일치");

        boolean hasChangedPassword = userLogService.hasChangedPassword(findAdmin.getUserSeq());
        boolean isFirstLogin = userLogService.isFirstLogin(findAdmin.getUserSeq());
        LoginResult response = completeLoginSuccess(findAdmin, RoleType.ADMIN, httpRequest, hasChangedPassword, isFirstLogin);
        log.info("{ AuthService } : 관리자 로그인 완료 {}", response.getIsFirstLogin());
        return response;
    }

    @Transactional
    public LoginResult superAdminLogin(LoginDto loginDto, HttpServletRequest httpRequest) {
        log.info("{ AuthService } : 슈퍼 관리자 로그인");
        UserAuthData findSuperAdmin = findUserOrThrowLoginFailed(() -> userFindService.findSuperAdminAuthByUserId(loginDto.userId()));

        verifyPassword(loginDto.encPwd(), findSuperAdmin, RoleType.SUPER_ADMIN);
        log.info("{ AuthService } : 비밀번호 일치");

        boolean hasChangedPassword = userLogService.hasChangedPassword(findSuperAdmin.getUserSeq());
        LoginResult response = completeLoginSuccess(findSuperAdmin, RoleType.SUPER_ADMIN, httpRequest, hasChangedPassword);
        log.info("{ AuthService } : 슈퍼 관리자 로그인 완료");
        return response;
    }

    @Transactional
    public AnonymousLoginResult anonymousLogin(String linkToken) {
        var data = resourceFindService.findResourceByShareToken(linkToken, false);
        DriveAssert.throwIfNull(data, AuthErrorCode.INVALID_TOKEN);

        var shareData = shareFindService.findNearestSelfOrShareByResource(data.getResourceSeq(), data.getUserSeq(), false);
        DriveAssert.throwIfFalse(shareData != null && shareData.getShareType() == ShareType.PUBLIC_LINK, AuthErrorCode.INVALID_TOKEN);

        TenantGuard.verifyOwnership(data.getTenantSeq(), AuthErrorCode.INVALID_TOKEN);

        UserDetailData user = userFindService.findAnonymousUser();
        LoginTokenResult tokens = issueTokens(user.getUserSeq(), RoleType.ANONYMOUS);
        BaseDomainData baseDomain = baseDomainFindService.findCurrent();
        String domain = (baseDomain != null) ? baseDomain.getBaseDomain() : null;
        return AnonymousLoginResult.of(user, tokens.accessToken(), tokens.refreshToken(), domain);
    }

    /**
     * 로그아웃은 인증 없이 받는다 — access 토큰이 만료돼도 쿠키는 지워야 하므로(쿠키 삭제는 컨트롤러).
     * 쿠키의 access 토큰이 유효할 때만 블랙리스트 등록·refresh 삭제·로그아웃 이력·감사 로그를 남긴다.
     * 만료·위조된 토큰이면 누구의 로그아웃인지 확정할 수 없어 기록하지 않는다. 인증 정보가 없어 감사 로그 actor 는 토큰의 사용자로 둔다.
     */
    @AuditLog
    @Transactional
    public void logout(String accessToken, HttpServletRequest httpRequest) {
        jwtProvider.parseIfAccessToken(accessToken).ifPresent(claims -> {
            log.info("{ AuthService } : 로그아웃 " + claims.userSeq());
            if (!blacklistTokenFindService.existsByTokenHash(new BlacklistFindQuery(TokenHasher.hash(accessToken)))) {
                blacklistTokenService.save(accessToken);
            }
            refreshTokenService.deleteRefreshTokenBySessionId(claims.sessionId());
            userLogService.registerLogoutLog(claims.userSeq(), httpRequest);
            loginAuditService.recordLogout(userFindService.findAuthByUserSeq(claims.userSeq()));
        });
        invalidateOAuth2SessionIfPresent(httpRequest);

        log.info("{ AuthService } : 로그아웃 완료");
    }

    /**
     * OAuth2 AS 세션 무효화 — 세션 브리지(OAuth2SessionBridgeUseCase)가 확립한 세션은
     * Drive JWT와 수명이 별개라, 남겨두면 로그아웃 후에도 같은 브라우저에서
     * /oauth2/authorize 진입 시 재로그인 없이 인가 코드가 발급된다 (공용 PC 위험).
     * 요청에 세션 쿠키가 없으면(브리지 미사용 사용자) 아무 일도 하지 않는다.
     */
    private void invalidateOAuth2SessionIfPresent(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @Transactional
    public LoginResult verifyMfaLogin(String challengeToken, String code, HttpServletRequest httpRequest) {
        log.info("[verifyMfaLogin - start]");

        int userSeq = parseUserSeqAndValidate(challengeToken, JwtType.MFA_CHALLENGE);
        loginRateLimitGuard.checkMfa(userSeq); // 서명 검증한 토큰의 사용자 기준 — 코드 비교 전에, 감사 로그(실패 기록) 전에
        UserAuthData user = userFindService.findAuthByUserSeq(userSeq);

        try {
            TenantGuard.verifyOwnership(user.getTenantSeq(), AuthErrorCode.INVALID_TOKEN_TYPE);
            mfaService.verify(user, code);
        } catch (RuntimeException e) {
            // 에러코드 종류(토큰 타입 불일치/MFA 코드 불일치 등)와 무관하게 로그인 실패로 기록한다.
            loginAuditService.recordLoginFailure(user);
            throw e;
        }
        log.info("[verifyMfaLogin - mfa verify success] userSeq: {}", userSeq);

        // MFA 는 사용자 로그인(웹 /login·SSO)에만 있다
        boolean hasChangedPassword = userLogService.hasChangedPassword(user.getUserSeq());
        LoginResult response = completeLoginSuccess(user, RoleType.USER, httpRequest, hasChangedPassword);
        log.info("[verifyMfaLogin - complete] userSeq: {}", userSeq);
        return response;
    }

    /**
     * 비밀번호 확인. 잠금은 사용자 로그인 화면에서만 — 계정 역할이 아니라 로그인 화면(loginRole) 기준(관리자·슈퍼관리자 화면은 잠금 없음).
     * <p>사용자 화면은 비교 <b>전에</b> 시도를 원자적으로 센다({@link UserService#countLoginAttempt}, 새 트랜잭션).
     * 예전처럼 요청 시작 때 읽은 횟수로 판단하면 동시에 보낸 요청이 모두 "잠기지 않음" 으로 통과해 잠금 한도보다 많이 비교할 수 있었다.
     * 마지막 실패 뒤 {@link LoginLockPolicy#LOCK_DURATION} 이 지나면 잠금이 풀린다.
     * 비밀번호가 맞으면 그 자리에서 횟수를 0 으로 되돌린다(새 트랜잭션) — MFA 단계가 남거나 뒤 단계가 실패해도 맞힌 시도가 잠금으로 쌓이지 않게.
     */
    private void verifyPassword(String comparePassword, UserAuthData findUser, RoleType loginRole) {
        if (RoleType.USER != loginRole) {
            if (findUser.getEncPwd().isSamePassword(comparePassword, ENCODER)) {
                return;
            }
            loginAuditService.recordLoginFailure(findUser);
            throw ApiRuntimeException.type(AuthErrorCode.LOGIN_FAILED);
        }

        int failureCount = countLoginAttempt(findUser);

        if (findUser.getEncPwd().isSamePassword(comparePassword, ENCODER)) {
            userService.resetLoginFailureCountOnPasswordMatch(findUser.getUserSeq());
            return;
        }

        // 5회째(잠기는 순간)는 "로그인 실패"와 "계정 잠금"이 동시에 일어난 별개의 사실이므로 둘 다 기록한다.
        if (failureCount >= LoginLockPolicy.MAX_FAILURES) {
            loginAuditService.recordLoginFailure(findUser);
            loginAuditService.recordAccountLocked(findUser);
            throw ApiRuntimeException.type(AuthErrorCode.ACCOUNT_LOCKED);
        }

        // 1~4회째는 전부 로그인 실패로 기록한다(4회째는 경고 응답이지만 감사 로그상 이벤트는 동일하게 LOGIN_FAILED).
        loginAuditService.recordLoginFailure(findUser);
        if (failureCount == LoginLockPolicy.MAX_FAILURES - 1) {
            throw ApiRuntimeException.type(AuthErrorCode.LOGIN_FAILURE_WARNING);
        }
        throw ApiRuntimeException.type(AuthErrorCode.LOGIN_FAILED);
    }

    /**
     * 이번 시도를 센 뒤 횟수. 잠겨 있으면 비밀번호를 비교하지 않고 ACCOUNT_LOCKED —
     * 이미 잠긴 계정의 재시도는 상태 전환(UNLOCKED->LOCKED)이 없으므로 ACCOUNT_LOCKED_STATUS_CHANGE 가 아니라 LOGIN_FAILED 로 기록한다.
     */
    private int countLoginAttempt(UserAuthData findUser) {
        LoginAttempt attempt = userService.countLoginAttempt(findUser.getUserSeq());
        switch (attempt.outcome()) {
            case COUNTED:
                return attempt.failureCount();
            case LOCKED:
                loginAuditService.recordLoginFailure(findUser);
                throw ApiRuntimeException.type(AuthErrorCode.ACCOUNT_LOCKED);
            case NOT_FOUND:
            default:
                // 새 트랜잭션에서 행이 보이지 않음 = 조회 직후 비활성·삭제됐거나, 이 트랜잭션이 아직 커밋하지 않은 계정(통합 테스트).
                // 이때만 읽어 둔 값으로 판단한다(원자적이지 않고 쓰지도 않음 — 예전 동작과 같다).
                Instant now = Instant.now();
                if (LoginLockPolicy.isLocked(findUser.getLoginFailureCount(), findUser.getLoginFailedAt(), now)) {
                    loginAuditService.recordLoginFailure(findUser);
                    throw ApiRuntimeException.type(AuthErrorCode.ACCOUNT_LOCKED);
                }
                return LoginLockPolicy.nextCount(findUser.getLoginFailureCount(), findUser.getLoginFailedAt(), now);
        }
    }

    private UserAuthData findUserOrThrowLoginFailed(java.util.function.Supplier<UserAuthData> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            throw ApiRuntimeException.type(AuthErrorCode.LOGIN_FAILED);
        }
    }

    public AuthLoginResult decideMfaResult(UserAuthData user) {
        if (MfaStatus.ENABLED == user.getMfaStatus()) {
            return issueMfaChallenge(user);
        }
        return issueMfaSetupChallenge(user);
    }

    private AuthLoginResult.MfaChallenge issueMfaChallenge(UserAuthData user) {
        log.info("{ AuthService } : MFA 활성화 사용자 - Challenge Token 발급");
        var result = mfaService.issueMfaChallenge(user.getUserSeq(), user.getMfaMethod());
        return AuthLoginResult.MfaChallenge.of(result.challengeToken(), result.expiresIn(), result.challengeType());
    }

    private AuthLoginResult.MfaSetupChallenge issueMfaSetupChallenge(UserAuthData user) {
        log.info("{ AuthService } : MFA 미설정 사용자 - Setup Challenge Token 발급");
        MfaSetupChallengeResult result = mfaService.issueMfaSetupChallenge(user.getUserSeq());
        return AuthLoginResult.MfaSetupChallenge.of(result.setupToken(), result.expiresIn());
    }

    @Transactional
    public LoginMfaSetupResult setupMfaForLogin(String setupToken, MfaMethod method) {
        log.info("[setupMfaForLogin - start]");
        
        int userSeq = parseUserSeqAndValidate(setupToken, JwtType.MFA_SETUP_CHALLENGE);
        UserDetailData user = userFindService.findDetailByUserSeq(userSeq);
        TenantGuard.verifyOwnership(user.getTenantSeq(), AuthErrorCode.INVALID_TOKEN_TYPE);

        MfaLoginSetupResult result = mfaService.setupMfaForLogin(userSeq, user.getUserId(), method);
        log.info("[setupMfaForLogin - complete] userSeq: {}", userSeq);
        return LoginMfaSetupResult.of(result.otpAuthUri(), result.confirmToken(), result.expiresIn());
    }

    @Transactional
    public LoginResult confirmMfaForLogin(String confirmToken, String code, MfaMethod method, HttpServletRequest httpRequest) {
        log.info("[confirmMfaForLogin - start]");
        
        int userSeq = parseUserSeqAndValidate(confirmToken, JwtType.MFA_CONFIRM_CHALLENGE);
        loginRateLimitGuard.checkMfa(userSeq);
        UserAuthData user = userFindService.findAuthByUserSeq(userSeq);

        try {
            TenantGuard.verifyOwnership(user.getTenantSeq(), AuthErrorCode.INVALID_TOKEN_TYPE);
            mfaService.confirmSetup(userSeq, code, method);
        } catch (RuntimeException e) {
            loginAuditService.recordLoginFailure(user);
            throw e;
        }
        log.info("[confirmMfaForLogin - mfa confirm success] userSeq: {}", userSeq);

        boolean hasChangedPassword = userLogService.hasChangedPassword(user.getUserSeq());
        LoginResult response = completeLoginSuccess(user, RoleType.USER, httpRequest, hasChangedPassword);
        log.info("[confirmMfaForLogin - complete] userSeq: {}", userSeq);
        return response;
    }

    @Transactional
    public LoginResult completeLoginSuccess(UserAuthData user, RoleType loginRole, HttpServletRequest httpRequest, Boolean hasChangedPassword) {
        return completeLoginSuccess(user, loginRole, httpRequest, hasChangedPassword, null);
    }

    /** 토큰 발급(로그인 역할 보유 확인 포함) + 실패 횟수 초기화(어느 화면이든 성공하면 0) — 응답의 roles 는 보유 역할 전부 */
    @Transactional
    public LoginResult completeLoginSuccess(UserAuthData user, RoleType loginRole, HttpServletRequest httpRequest, Boolean hasChangedPassword, Boolean isFirstLogin) {
        LoginTokenResult result = issueLoginTokensAndLog(user, loginRole, httpRequest);
        userService.resetLoginFailureCount(user.getUserSeq());

        BaseDomainData baseDomain = baseDomainFindService.findCurrent();
        String domain = (baseDomain != null) ? baseDomain.getBaseDomain() : null;
        return LoginResult.of(user, heldRoles(user.getUserSeq()), result.accessToken(), result.refreshToken(),
                passwordChangeNotPrompted(user, hasChangedPassword), domain, isFirstLogin);
    }

    /**
     * 비밀번호를 바꿀 수 없는 계정(SYSTEM_MANAGED 보호 계정)은 초기 비밀번호여도 변경 유도를 하지 않는다 — 화면은 false 일 때만 변경 창을 띄운다.
     * 바꿀 수 없는지는 false 일 때만 본다(이미 바꾼 계정은 조회 없음).
     */
    private Boolean passwordChangeNotPrompted(UserAuthData user, Boolean hasChangedPassword) {
        if (Boolean.FALSE.equals(hasChangedPassword) && !tenantPolicy.isSelfPasswordChangeable(user.getUserSeq())) {
            return Boolean.TRUE;
        }
        return hasChangedPassword;
    }

    /** 응답용 보유 역할 전부(enum 순서) — 토큰 role 클레임(로그인 역할)과 별개 */
    public List<RoleType> heldRoles(Integer userSeq) {
        return userRoleFindService.findRoles(userSeq).stream().sorted().toList();
    }

    /** access·refresh 발급 — 두 토큰 모두 role 클레임 = 로그인 역할(필터·재발급이 이 역할 보유를 다시 확인한다) */
    @Transactional
    public LoginTokenResult issueTokens(Integer userSeq, RoleType role) {
        String sessionId    = UuidV7Generator.generate();
        String accessToken  = jwtProvider.createAccessToken(userSeq, sessionId, role);
        String refreshToken = jwtProvider.createRefreshToken(userSeq, sessionId, role);
        refreshTokenService.saveRefreshToken(userSeq, sessionId, refreshToken);
        return new LoginTokenResult(accessToken, refreshToken);
    }

    @Transactional
    public LoginResult loginWithEntryKey(String entryKey, HttpServletRequest httpRequest) {
        TenantAccessTokenConsumeResult token = tenantAccessTokenService.consumeAndGet(entryKey);
        UserAuthData superAdmin = userFindService.findAuthByUserSeq(token.superAdminUserSeq());
        boolean hasChangedPassword = userLogService.hasChangedPassword(superAdmin.getUserSeq());
        return completeLoginSuccess(superAdmin, RoleType.SUPER_ADMIN, httpRequest, hasChangedPassword);
    }

    /**
     * 로그인 토큰 발급 + 로그인 로그·감사. 로그인 역할을 계정이 보유하지 않으면 LOGIN_FAILED —
     * 비밀번호 로그인은 조회가 이미 역할로 거르지만, SSO·MFA·entry-key·API Key 교환까지 한 곳에서 막는다.
     */
    @AuditLog
    @Transactional
    public LoginTokenResult issueLoginTokensAndLog(UserIdentity user, RoleType loginRole, HttpServletRequest httpRequest) {
        DriveAssert.throwIfFalse(userRoleFindService.holds(user.getUserSeq(), loginRole), AuthErrorCode.LOGIN_FAILED);
        var result = issueTokens(user.getUserSeq(), loginRole);
        userLogService.registerLoginLog(user.getUserSeq(), httpRequest);
        // 로그인 처리 중이라 SecurityContextHolder에 인증 정보가 없어 actor를 직접 override한다.
        loginAuditService.recordLoginSuccess(user);
        return result;
    }
    
    private int parseUserSeqAndValidate(String token, JwtType expectedType) {
        try {
            return jwtProvider.parseUserSeqAndValidate(token, expectedType);
        } catch (ExpiredJwtException e) {
            log.warn("[parseUserSeqAndValidate - expire token]");
            throw ApiRuntimeException.type(AuthErrorCode.CHALLENGE_TOKEN_EXPIRED);
        } catch (JwtException e) {
            log.warn("[parseUserSeqAndValidate - invalid token]");
            throw ApiRuntimeException.type(AuthErrorCode.INVALID_TOKEN_TYPE);
        }
    }
}
