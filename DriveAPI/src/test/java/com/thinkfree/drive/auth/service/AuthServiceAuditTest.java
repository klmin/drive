package com.thinkfree.drive.auth.service;
import com.thinkfree.drive.common.AuditMockTest;

import com.thinkfree.drive.auth.dto.LoginDto;
import com.thinkfree.drive.auditlog.service.LoginAuditService;
import com.thinkfree.drive.auth.exception.AuthErrorCode;
import com.thinkfree.drive.auth.token.blacklist.service.BlacklistTokenService;
import com.thinkfree.drive.auth.token.refresh.service.impl.DefaultRefreshTokenService;
import com.thinkfree.drive.auth.utils.JwtProvider;
import com.thinkfree.drive.basedomain.service.BaseDomainFindService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.mfa.service.MfaService;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.share.service.ShareFindService;
import com.thinkfree.drive.superadmin.token.service.TenantAccessTokenService;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.user.entity.Password;
import com.thinkfree.drive.user.data.LoginAttempt;
import com.thinkfree.drive.user.data.UserAuthData;
import com.thinkfree.drive.user.util.LoginLockPolicy;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.role.service.UserRoleFindService;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.user.service.UserService;
import com.thinkfree.drive.userlog.service.UserLogService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AuthService의 로그인 흐름이 <b>어떤 LOGIN 감사 이벤트를 언제 기록하는지</b>를 검증한다
 * (LoginAuditService 호출 기준). 각 이벤트가 채우는 context 내용(actor override·before/after·outcome 등)은
 * {@code DefaultLoginAuditServiceTest}에서 검증한다.
 * <p>LOGOUT은 선언형(@AuditLog(eventName=LOGOUT))이라 AuditLogAspectTest에서 다룬다.
 */
@DisplayName("AuthService 로그인 감사 이벤트 기록 흐름 테스트")
class AuthServiceAuditTest extends AuditMockTest {

    private static final int TENANT = 1;

    @Mock private JwtProvider jwtProvider;
    @Mock private DefaultRefreshTokenService refreshTokenService;
    @Mock private UserFindService userFindService;
    @Mock private UserLogService userLogService;
    @Mock private LoginAuditService loginAuditService;
    @Mock private UserService userService;
    @Mock private BlacklistTokenService blacklistTokenService;
    @Mock private MfaService mfaService;
    @Mock private SystemConfigFindService systemConfigFindService;
    @Mock private LoginConfigService loginConfigService;
    @Mock private TenantAccessTokenService tenantAccessTokenService;
    @Mock private BaseDomainFindService baseDomainFindService;
    @Mock private ResourceFindService resourceFindService;
    @Mock private ShareFindService shareFindService;
    @Mock private UserRoleFindService userRoleFindService;

    @InjectMocks
    private AuthService authService;

    private UserAuthData userMock(int seq, RoleType role) {
        UserAuthData u = Mockito.mock(UserAuthData.class);
        given(u.getUserSeq()).willReturn(seq);
        given(u.getUserId()).willReturn("user" + seq + "@thinkfree.com");
        given(u.getUserName()).willReturn("사용자" + seq);
        return u;
    }

    @Test
    @DisplayName("issueLoginTokensAndLog - LOGIN_SUCCESS를 기록한다")
    void issueLoginTokensAndLog_recordsLoginSuccess() {
        UserAuthData user = userMock(20, RoleType.USER);

        given(userRoleFindService.holds(20, RoleType.USER)).willReturn(true);
        authService.issueLoginTokensAndLog(user, RoleType.USER, Mockito.mock(HttpServletRequest.class));

        verify(loginAuditService).recordLoginSuccess(user);
    }

    @Test
    @DisplayName("login - 4회째 비밀번호 실패(경고)는 LOGIN_FAILED만 기록하고 계정 잠금은 없다")
    void login_fourthFailure_recordsLoginFailedOnly() {
        UserAuthData user = userMock(20, RoleType.USER);
        Password pwd = Mockito.mock(Password.class);
        given(user.getEncPwd()).willReturn(pwd);
        given(pwd.isSamePassword(any(), any())).willReturn(false); // 비밀번호 불일치
        given(userService.countLoginAttempt(20)).willReturn(LoginAttempt.counted(4)); // 이번이 4회째
        given(userFindService.findActiveAuthInTenant(any(), anyInt())).willReturn(user);

        assertThatThrownBy(() -> authService.login(new LoginDto("user20@thinkfree.com", "wrong"), TENANT, Mockito.mock(HttpServletRequest.class)))
                .isInstanceOf(ApiRuntimeException.class)
                .hasMessage(AuthErrorCode.LOGIN_FAILURE_WARNING.getMessage());

        verify(loginAuditService).recordLoginFailure(user);
        verify(loginAuditService, never()).recordAccountLocked(any());
    }

    @Test
    @DisplayName("login - 5회째 실패(잠기는 순간)는 LOGIN_FAILED + 계정 잠금을 함께 기록한다")
    void login_fifthFailure_recordsLoginFailedAndAccountLocked() {
        UserAuthData user = userMock(20, RoleType.USER);
        Password pwd = Mockito.mock(Password.class);
        given(user.getEncPwd()).willReturn(pwd);
        given(pwd.isSamePassword(any(), any())).willReturn(false);
        given(userService.countLoginAttempt(20)).willReturn(LoginAttempt.counted(5)); // 이번이 5회째(잠김)
        given(userFindService.findActiveAuthInTenant(any(), anyInt())).willReturn(user);

        assertThatThrownBy(() -> authService.login(new LoginDto("user20@thinkfree.com", "wrong"), TENANT, Mockito.mock(HttpServletRequest.class)))
                .isInstanceOf(ApiRuntimeException.class)
                .hasMessage(AuthErrorCode.ACCOUNT_LOCKED.getMessage());

        verify(loginAuditService).recordLoginFailure(user);
        verify(loginAuditService).recordAccountLocked(user);
    }

    @Test
    @DisplayName("login - 이미 잠긴 계정은 비밀번호를 비교하지 않고 ACCOUNT_LOCKED, LOGIN_FAILED 만 기록(잠금 전환 아님)")
    void login_alreadyLocked_skipsPasswordCompare() {
        UserAuthData user = userMock(20, RoleType.USER);
        Password pwd = Mockito.mock(Password.class);
        given(user.getEncPwd()).willReturn(pwd);
        given(userService.countLoginAttempt(20)).willReturn(LoginAttempt.locked());
        given(userFindService.findActiveAuthInTenant(any(), anyInt())).willReturn(user);

        assertThatThrownBy(() -> authService.login(new LoginDto("user20@thinkfree.com", "right"), TENANT, Mockito.mock(HttpServletRequest.class)))
                .isInstanceOf(ApiRuntimeException.class)
                .hasMessage(AuthErrorCode.ACCOUNT_LOCKED.getMessage());

        verify(pwd, never()).isSamePassword(any(), any());
        verify(loginAuditService).recordLoginFailure(user);
        verify(loginAuditService, never()).recordAccountLocked(any());
        verify(userService, never()).resetLoginFailureCountOnPasswordMatch(any());
    }

    @Test
    @DisplayName("login - 5회째 시도라도 비밀번호가 맞으면 그 자리에서 실패 횟수를 초기화한다(MFA 전 단계에서 잠금으로 남지 않게)")
    void login_passwordMatch_resetsFailureCountBeforeMfa() {
        UserAuthData user = userMock(20, RoleType.USER);
        Password pwd = Mockito.mock(Password.class);
        given(user.getEncPwd()).willReturn(pwd);
        given(pwd.isSamePassword(any(), any())).willReturn(true);
        given(userService.countLoginAttempt(20)).willReturn(LoginAttempt.counted(5));
        given(userFindService.findActiveAuthInTenant(any(), anyInt())).willReturn(user);
        // MFA 단계가 남는 테넌트 — 비밀번호 확인 뒤 예외로 끝나도 초기화는 이미 별도 트랜잭션으로 끝났다
        given(systemConfigFindService.findMfaStatusConfig(TENANT)).willThrow(new IllegalStateException("mfa lookup failed"));

        assertThatThrownBy(() -> authService.login(new LoginDto("user20@thinkfree.com", "right"), TENANT, Mockito.mock(HttpServletRequest.class)))
                .isInstanceOf(IllegalStateException.class);

        verify(userService).resetLoginFailureCountOnPasswordMatch(20);
        verify(loginAuditService, never()).recordLoginFailure(any());
        verify(loginAuditService, never()).recordAccountLocked(any());
    }

    @Test
    @DisplayName("login - 새 트랜잭션에서 행이 안 보이면(NOT_FOUND) 읽어 둔 값으로 판단 — 15분 안의 5회는 잠김, 지난 5회는 1회째로 다시 센다")
    void login_notFound_fallsBackToReadValues() {
        UserAuthData locked = userMock(20, RoleType.USER);
        given(locked.getLoginFailureCount()).willReturn(5);
        given(locked.getLoginFailedAt()).willReturn(Instant.now().minusSeconds(60));
        given(userService.countLoginAttempt(20)).willReturn(LoginAttempt.notFound());
        given(userFindService.findActiveAuthInTenant(any(), anyInt())).willReturn(locked);

        assertThatThrownBy(() -> authService.login(new LoginDto("user20@thinkfree.com", "right"), TENANT, Mockito.mock(HttpServletRequest.class)))
                .isInstanceOf(ApiRuntimeException.class)
                .hasMessage(AuthErrorCode.ACCOUNT_LOCKED.getMessage());

        UserAuthData expired = userMock(21, RoleType.USER);
        Password pwd = Mockito.mock(Password.class);
        given(expired.getEncPwd()).willReturn(pwd);
        given(pwd.isSamePassword(any(), any())).willReturn(false);
        given(expired.getLoginFailureCount()).willReturn(5);
        given(expired.getLoginFailedAt()).willReturn(Instant.now().minus(LoginLockPolicy.LOCK_DURATION).minusSeconds(60));
        given(userService.countLoginAttempt(21)).willReturn(LoginAttempt.notFound());
        given(userFindService.findActiveAuthInTenant(any(), anyInt())).willReturn(expired);

        assertThatThrownBy(() -> authService.login(new LoginDto("user21@thinkfree.com", "wrong"), TENANT, Mockito.mock(HttpServletRequest.class)))
                .isInstanceOf(ApiRuntimeException.class)
                .hasMessage(AuthErrorCode.LOGIN_FAILED.getMessage());
    }

    @Test
    @DisplayName("adminLogin - 관리자 비밀번호 실패는 계정 잠금 없이 LOGIN_FAILED만 기록한다")
    void adminLogin_wrongPassword_recordsLoginFailedWithoutLock() {
        UserAuthData admin = userMock(21, RoleType.ADMIN);
        Password pwd = Mockito.mock(Password.class);
        given(admin.getEncPwd()).willReturn(pwd);
        given(pwd.isSamePassword(any(), any())).willReturn(false);
        given(userFindService.findActiveAdminAuthInTenant(any(), anyInt())).willReturn(admin);

        assertThatThrownBy(() -> authService.adminLogin(new LoginDto("user21@thinkfree.com", "wrong"), TENANT, Mockito.mock(HttpServletRequest.class)))
                .isInstanceOf(ApiRuntimeException.class)
                .hasMessage(AuthErrorCode.LOGIN_FAILED.getMessage());

        verify(loginAuditService).recordLoginFailure(admin);
        verify(loginAuditService, never()).recordAccountLocked(any());
    }
}
