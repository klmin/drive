package com.thinkfree.drive.auth.service;

import java.time.Instant;
import com.thinkfree.drive.auth.dto.LoginDto;
import com.thinkfree.drive.auth.exception.AuthErrorCode;
import com.thinkfree.drive.auth.result.LoginResult;
import com.thinkfree.drive.auth.utils.JwtProvider;
import com.thinkfree.drive.common.ServiceTest;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.user.entity.Password;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.SignupType;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.user.role.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import static com.thinkfree.drive.global.utils.PasswordEncoderUtils.ENCODER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 다중 role 로그인(ROLE_DESIGN_FINAL §4-1·4-2) — 로그인 화면 역할을 tb_user_role 로 확인, 토큰·응답 역할 = 화면 역할,
 * 잠금은 사용자 화면에서만(계정 역할 무관), 어느 화면이든 성공하면 실패 횟수 0, 미보유 역할 토큰 발급은 LOGIN_FAILED. PostgreSQL Testcontainers, 롤백.
 */
@DisplayName("다중 role 로그인 — 화면 역할 기준 조회·토큰·잠금")
class MultiRoleLoginTest extends ServiceTest {

    private static final int TENANT = 1;
    private static final String PW = "PgMr!pass1";

    @Autowired
    private AuthService authService;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private JdbcTemplate jdbc;

    private User insert(String userId, RoleType primary, RoleType... more) {
        User user = insertUserWithRole(User.createUser(userId, TENANT, Password.encrypt(PW, ENCODER), SignupType.LOCAL, userId, UserStatus.ACTIVE,
                primary == RoleType.USER ? 1024L : 0L, 1, null, null, null), primary);
        for (RoleType role : more) {
            userRoleRepository.grant(UserRole.grant(user.getUserSeq(), role, 1));
        }
        return user;
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr("121.157.114.75");
        return r;
    }

    private int failureCount(int userSeq) {
        return jdbc.queryForObject("SELECT login_failure_count FROM tb_user WHERE user_seq = ?", Integer.class, userSeq);
    }

    @Test
    @DisplayName("USER+ADMIN 보유 — 사용자 화면은 USER, 관리자 화면은 ADMIN 토큰, 응답 roles 는 둘 다 보유 역할 전부")
    void bothRolesLoginEachScreen() {
        User user = insert("mr-both@test.com", RoleType.USER, RoleType.ADMIN);

        LoginResult asUser = (LoginResult) authService.login(new LoginDto(user.getUserId(), PW), TENANT, request());
        LoginResult asAdmin = authService.adminLogin(new LoginDto(user.getUserId(), PW), TENANT, request());

        assertThat(asUser.getRoles()).containsExactly(RoleType.ADMIN, RoleType.USER);
        assertThat(jwtProvider.getRole(asUser.getAccessToken())).isEqualTo(RoleType.USER);
        assertThat(jwtProvider.getRole(asUser.getRefreshToken())).isEqualTo(RoleType.USER);
        assertThat(asAdmin.getRoles()).containsExactly(RoleType.ADMIN, RoleType.USER);
        assertThat(jwtProvider.getRole(asAdmin.getAccessToken())).isEqualTo(RoleType.ADMIN);
    }

    @Test
    @DisplayName("보유 역할로 조회 — ADMIN 만 보유하면 사용자 화면 LOGIN_FAILED, USER 만이면 관리자 화면 LOGIN_FAILED")
    void notHeldScreenFails() {
        User admin = insert("mr-admin@test.com", RoleType.ADMIN);
        User user = insert("mr-user@test.com", RoleType.USER);
        // USER 로 만든 뒤 역할을 ADMIN 으로 바꾼 계정도 관리자 화면 통과·사용자 화면 실패
        User columnUser = insert("mr-col@test.com", RoleType.USER);
        userRoleRepository.revoke(columnUser.getUserSeq(), RoleType.USER);
        userRoleRepository.grant(UserRole.grant(columnUser.getUserSeq(), RoleType.ADMIN, 1));

        var e1 = catchThrowableOfType(ApiRuntimeException.class, () -> authService.login(new LoginDto(admin.getUserId(), PW), TENANT, request()));
        var e2 = catchThrowableOfType(ApiRuntimeException.class, () -> authService.adminLogin(new LoginDto(user.getUserId(), PW), TENANT, request()));
        var e3 = catchThrowableOfType(ApiRuntimeException.class, () -> authService.login(new LoginDto(columnUser.getUserId(), PW), TENANT, request()));
        LoginResult ok = authService.adminLogin(new LoginDto(columnUser.getUserId(), PW), TENANT, request());

        assertThat(e1.getCode()).isEqualTo(AuthErrorCode.LOGIN_FAILED);
        assertThat(e2.getCode()).isEqualTo(AuthErrorCode.LOGIN_FAILED);
        assertThat(e3.getCode()).isEqualTo(AuthErrorCode.LOGIN_FAILED);
        assertThat(jwtProvider.getRole(ok.getAccessToken())).isEqualTo(RoleType.ADMIN);
        assertThat(ok.getRoles()).containsExactly(RoleType.ADMIN);
    }

    @Test
    @DisplayName("잠금은 로그인 화면 기준 — USER+ADMIN 계정이 사용자 화면에서 잠겨도 관리자 화면은 성공하고 실패 횟수 0, 관리자 화면 실패는 횟수 미증가")
    void lockFollowsScreen() {
        User user = insert("mr-lock@test.com", RoleType.USER, RoleType.ADMIN);
        for (int i = 0; i < 5; i++) {
            userRepository.countLoginAttempt(user.getUserSeq(), Instant.now());
        }

        var locked = catchThrowableOfType(ApiRuntimeException.class, () -> authService.login(new LoginDto(user.getUserId(), PW), TENANT, request()));
        assertThat(locked.getCode()).isEqualTo(AuthErrorCode.ACCOUNT_LOCKED);

        var adminWrong = catchThrowableOfType(ApiRuntimeException.class, () -> authService.adminLogin(new LoginDto(user.getUserId(), "wrong"), TENANT, request()));
        assertThat(adminWrong.getCode()).as("관리자 화면은 잠금 없이 로그인 실패").isEqualTo(AuthErrorCode.LOGIN_FAILED);
        assertThat(failureCount(user.getUserSeq())).as("관리자 화면 실패는 횟수 미증가").isEqualTo(5);

        authService.adminLogin(new LoginDto(user.getUserId(), PW), TENANT, request());
        assertThat(failureCount(user.getUserSeq())).as("어느 화면이든 성공하면 0").isZero();
        assertThat(jwtProvider.getRole(((LoginResult) authService.login(new LoginDto(user.getUserId(), PW), TENANT, request())).getAccessToken()))
                .as("풀린 뒤 사용자 화면도 성공").isEqualTo(RoleType.USER);
    }

    @Test
    @DisplayName("토큰 발급 공통(SSO·MFA·entry-key·API Key 교환) — 로그인 역할을 보유하지 않으면 LOGIN_FAILED")
    void issueRequiresHeldRole() {
        User admin = insert("mr-sso@test.com", RoleType.ADMIN);
        var auth = userQueryRepository.findActiveAuthByUserId(TENANT, admin.getUserId(), RoleType.ADMIN);

        var e = catchThrowableOfType(ApiRuntimeException.class, () -> authService.issueLoginTokensAndLog(auth, RoleType.USER, request()));
        assertThat(e.getCode()).isEqualTo(AuthErrorCode.LOGIN_FAILED);
        assertThat(jwtProvider.getRole(authService.issueLoginTokensAndLog(auth, RoleType.ADMIN, request()).accessToken())).isEqualTo(RoleType.ADMIN);
    }
}
