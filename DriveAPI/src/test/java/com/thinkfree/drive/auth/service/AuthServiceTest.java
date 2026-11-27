package com.thinkfree.drive.auth.service;

import com.thinkfree.drive.auth.dto.LoginDto;
import com.thinkfree.drive.auth.result.AuthLoginResult;
import com.thinkfree.drive.auth.result.LoginResult;
import com.thinkfree.drive.auditlog.context.AuditLogContextHolder;
import com.thinkfree.drive.auth.exception.AuthErrorCode;
import com.thinkfree.drive.auth.utils.JwtProvider;
import com.thinkfree.drive.auth.utils.TokenHasher;
import com.thinkfree.drive.common.ServiceTest;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.superadmin.exception.SuperAdminErrorCode;
import com.thinkfree.drive.superadmin.token.entity.TenantAccessToken;
import com.thinkfree.drive.superadmin.token.repository.TenantAccessTokenRepository;
import com.thinkfree.drive.superadmin.token.service.TenantAccessTokenService;
import com.thinkfree.drive.user.data.UserAuthData;
import com.thinkfree.drive.user.enums.RoleType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;

import static com.thinkfree.drive.fixture.UserFixture.TEST_ADMIN_01;
import static com.thinkfree.drive.fixture.UserFixture.TEST_SUPER_ADMIN_01;
import static com.thinkfree.drive.fixture.UserFixture.TEST_USER_01;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Auth [Service Layer] -> AuthService 테스트")
public class AuthServiceTest extends ServiceTest {
    @Autowired
    private AuthService authService;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private TenantAccessTokenService tenantAccessTokenService;

    @Autowired
    private TenantAccessTokenRepository tenantAccessTokenRepository;

    private UserAuthData testUser1;
    
    private static final int TENANT = 1;

    @BeforeEach
    void setup() {
        insertUserWithRole(TEST_USER_01.toUser(), RoleType.USER);
        testUser1 = userQueryRepository.findActiveAuthByUserId(TEST_USER_01.getTenantSeq(), TEST_USER_01.getUserId(), RoleType.USER);
    }

    @AfterEach
    void clearAuditLogContext() {
        AuditLogContextHolder.clear();
    }

    @Nested
    @DisplayName("로그인")
    class login {

        @Test
        @DisplayName("존재하지 않는 아이디로 로그인하면 실패한다")
        void throwExceptionByUserNotFound() {
            // given
            MockHttpServletRequest mockRequest = new MockHttpServletRequest();
            mockRequest.setRemoteAddr("121.157.114.75");

            // when - then
            assertThatThrownBy(() -> authService.login(new LoginDto("notexist@test.com", "password"), TENANT, mockRequest))
                    .isInstanceOf(ApiRuntimeException.class)
                    .hasMessage(AuthErrorCode.LOGIN_FAILED.getMessage());
        }

        @Test
        @DisplayName("비밀번호가 일치하지 않으면 로그인에 실패한다")
        void throwExceptionByWrongPassword() {
            // given
            MockHttpServletRequest mockRequest = new MockHttpServletRequest();
            mockRequest.setRemoteAddr("121.157.114.75");

            // when - then
            assertThatThrownBy(() -> authService.login(createWrongLoginRequest(), TENANT, mockRequest))
                    .isInstanceOf(ApiRuntimeException.class)
                    .hasMessage(AuthErrorCode.LOGIN_FAILED.getMessage());
        }

        // 감사 로그 기록 내용(LOGIN_FAILED)은 AuthServiceAuditTest에서 검증 - 여기선 경고 동작만 확인
        @Test
        @DisplayName("4회 연속 로그인 실패 시 경고 에러가 발생한다")
        void throwExceptionByLoginFailureWarning() {
            // given
            MockHttpServletRequest mockRequest = new MockHttpServletRequest();
            mockRequest.setRemoteAddr("121.157.114.75");

            // 서비스의 countLoginAttempt는 REQUIRES_NEW라 테스트 트랜잭션 내 INSERT를 볼 수 없으므로(그때는 읽어 둔 값으로 판단)
            // repository를 직접 호출해 외부 트랜잭션 안에서 count를 3으로 세팅
            for (int i = 0; i < 3; i++) {
                userRepository.countLoginAttempt(testUser1.getUserSeq(), Instant.now());
            }

            // when - then: 4회째 실패 → 경고
            assertThatThrownBy(() -> authService.login(createWrongLoginRequest(), TENANT, mockRequest))
                    .isInstanceOf(ApiRuntimeException.class)
                    .hasMessage(AuthErrorCode.LOGIN_FAILURE_WARNING.getMessage());
        }

        // 감사 로그 기록 내용(LOGIN_FAILED + ACCOUNT_LOCKED_STATUS_CHANGE)은 AuthServiceAuditTest에서 검증 - 여기선 잠금 동작만 확인
        @Test
        @DisplayName("5회 연속 로그인 실패 시 계정이 잠긴다")
        void throwExceptionByAccountLockedAfterFiveFailures() {
            // given
            MockHttpServletRequest mockRequest = new MockHttpServletRequest();
            mockRequest.setRemoteAddr("121.157.114.75");

            for (int i = 0; i < 4; i++) {
                userRepository.countLoginAttempt(testUser1.getUserSeq(), Instant.now());
            }

            // when - then: 5회째 실패 → 계정 잠김
            assertThatThrownBy(() -> authService.login(createWrongLoginRequest(), TENANT, mockRequest))
                    .isInstanceOf(ApiRuntimeException.class)
                    .hasMessage(AuthErrorCode.ACCOUNT_LOCKED.getMessage());
        }

        @Test
        @DisplayName("계정이 잠긴 상태에서 올바른 비밀번호로 로그인해도 실패한다")
        void throwExceptionByAccountLockedEvenWithCorrectPassword() {
            // given
            MockHttpServletRequest mockRequest = new MockHttpServletRequest();
            mockRequest.setRemoteAddr("121.157.114.75");

            for (int i = 0; i < 5; i++) {
                userRepository.countLoginAttempt(testUser1.getUserSeq(), Instant.now());
            }

            // when - then: 올바른 비밀번호여도 잠김
            assertThatThrownBy(() -> authService.login(createLoginRequest(), TENANT, mockRequest))
                    .isInstanceOf(ApiRuntimeException.class)
                    .hasMessage(AuthErrorCode.ACCOUNT_LOCKED.getMessage());
        }

        @Test
        @DisplayName("로그인 성공 시 실패 횟수가 초기화된다")
        void resetLoginFailureCountOnSuccess() {
            // given
            MockHttpServletRequest mockRequest = new MockHttpServletRequest();
            mockRequest.setRemoteAddr("121.157.114.75");

            for (int i = 0; i < 2; i++) {
                userRepository.countLoginAttempt(testUser1.getUserSeq(), Instant.now());
            }

            // when
            authService.login(createLoginRequest(), TENANT, mockRequest);

            // then
            UserAuthData updatedUser = userQueryRepository.findActiveAuthByUserId(TEST_USER_01.getTenantSeq(), TEST_USER_01.getUserId(), RoleType.USER);
            assertThat(updatedUser.getLoginFailureCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("MFA 비활성 사용자 로그인에 성공한다")
        void success() {
            // given
            MockHttpServletRequest mockRequest = new MockHttpServletRequest();
            mockRequest.setRemoteAddr("121.157.114.75");

            // when
            AuthLoginResult authLoginResult = authService.login(createLoginRequest(), TENANT, mockRequest);

            // then
            assertThat(authLoginResult).isInstanceOf(LoginResult.class);

            if (authLoginResult instanceof LoginResult response) {
                Assertions.assertAll(
                        () -> assertThat(response.getUserSeq()).isEqualTo(testUser1.getUserSeq()),
                        () -> assertThat(response.getUserId()).isEqualTo(testUser1.getUserId()),
                        () -> assertThat(response.getUserName()).isEqualTo(testUser1.getUserName()),
                        () -> assertThat(response.getTotalQuotaByte()).isEqualTo(testUser1.getTotalQuotaByte()),
                        () -> assertThat(response.getUseQuotaByte()).isEqualTo(testUser1.getUseQuotaByte()),
                        () -> assertThat(response.getRoles()).contains(RoleType.USER),
                        () -> assertThat(jwtProvider.getId(response.getAccessToken())).isEqualTo(testUser1.getUserSeq()),
                        () -> assertThat(jwtProvider.getId(response.getRefreshToken())).isEqualTo(testUser1.getUserSeq()),
                        () -> {
                            String sessionId = jwtProvider.getSessionId(response.getRefreshToken());
                            assertThat(refreshTokenValidInDb(sessionId, response.getRefreshToken())).isTrue();
                        },
                        () -> assertThat(response.getHasChangedPassword()).isFalse()
                );
            }
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class logout {
        @Test
        @DisplayName("로그아웃에 성공한다")
        void success() {
            // given
            MockHttpServletRequest mockRequest = new MockHttpServletRequest();
            mockRequest.setRemoteAddr("121.157.114.75");
            AuthLoginResult authLoginResult = authService.login(createLoginRequest(), TENANT, mockRequest);
            LoginResult loginResponse = (LoginResult) authLoginResult;

            // when
            authService.logout(loginResponse.getAccessToken(), mockRequest);

            // then
            String sessionId = jwtProvider.getSessionId(loginResponse.getAccessToken());
            assertThat(refreshTokenValidInDb(sessionId, loginResponse.getRefreshToken())).isFalse();
        }

        @Test
        @DisplayName("같은 토큰으로 두 번 로그아웃해도 실패하지 않는다(블랙리스트 중복 등록 안 함)")
        void twice() {
            MockHttpServletRequest mockRequest = new MockHttpServletRequest();
            mockRequest.setRemoteAddr("121.157.114.75");
            LoginResult loginResponse = (LoginResult) authService.login(createLoginRequest(), TENANT, mockRequest);

            authService.logout(loginResponse.getAccessToken(), mockRequest);

            assertThatCode(() -> authService.logout(loginResponse.getAccessToken(), mockRequest)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"not-a-jwt"})
        @DisplayName("토큰이 없거나 유효하지 않으면 아무것도 기록하지 않고 끝난다(쿠키 삭제는 컨트롤러)")
        void invalidToken(String accessToken) {
            assertThatCode(() -> authService.logout(accessToken, new MockHttpServletRequest())).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("관리자 로그인")
    class adminLogin {

        @BeforeEach
        void setup() {
            insertUserWithRole(TEST_ADMIN_01.toUser(), RoleType.ADMIN);
        }

        @Test
        @DisplayName("ADMIN 계정은 5회 이상 비밀번호 실패해도 ACCOUNT_LOCKED가 아닌 LOGIN_FAILED가 발생한다")
        void admin_doesNotGetLockedAfterMultipleFailures() {
            // given
            MockHttpServletRequest mockRequest = new MockHttpServletRequest();
            mockRequest.setRemoteAddr("121.157.114.75");

            for (int i = 0; i < 5; i++) {
                userRepository.countLoginAttempt(userQueryRepository.findActiveAuthByUserId(TEST_ADMIN_01.getTenantSeq(), TEST_ADMIN_01.getUserId(), RoleType.ADMIN).getUserSeq(), Instant.now());
            }

            // when - then: 실패 횟수와 무관하게 계정 잠김이 아닌 로그인 실패
            assertThatThrownBy(() -> authService.adminLogin(createWrongAdminLoginRequest(), TENANT, mockRequest))
                    .isInstanceOf(ApiRuntimeException.class)
                    .hasMessage(AuthErrorCode.LOGIN_FAILED.getMessage());
        }

        @Test
        @DisplayName("ADMIN 계정은 비밀번호 실패 시 실패 횟수가 증가하지 않는다")
        void admin_doesNotIncrementFailureCount() {
            // given
            MockHttpServletRequest mockRequest = new MockHttpServletRequest();
            mockRequest.setRemoteAddr("121.157.114.75");

            // when
            assertThatThrownBy(() -> authService.adminLogin(createWrongAdminLoginRequest(), TENANT, mockRequest))
                    .isInstanceOf(ApiRuntimeException.class);

            // then
            UserAuthData updatedAdmin = userQueryRepository.findActiveAuthByUserId(TEST_ADMIN_01.getTenantSeq(), TEST_ADMIN_01.getUserId(), RoleType.ADMIN);
            assertThat(updatedAdmin.getLoginFailureCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("관리자 로그인에 성공한다")
        void success() {
            // given
            MockHttpServletRequest mockRequest = new MockHttpServletRequest();
            mockRequest.setRemoteAddr("121.157.114.75");
            UserAuthData admin = userQueryRepository.findActiveAuthByUserId(TEST_ADMIN_01.getTenantSeq(), TEST_ADMIN_01.getUserId(), RoleType.ADMIN);

            // when
            LoginResult response = authService.adminLogin(createAdminLoginRequest(), TENANT, mockRequest);

            // then
            Assertions.assertAll(
                    () -> assertThat(response.getUserSeq()).isEqualTo(admin.getUserSeq()),
                    () -> assertThat(response.getUserId()).isEqualTo(admin.getUserId()),
                    () -> assertThat(response.getRoles()).contains(RoleType.ADMIN),
                    () -> assertThat(jwtProvider.getId(response.getAccessToken())).isEqualTo(admin.getUserSeq()),
                    () -> assertThat(response.getHasChangedPassword()).isFalse(),
                    () -> assertThat(response.getIsFirstLogin()).isTrue()
            );
        }

        @Test
        @DisplayName("관리자가 두 번째 로그인 시 isFirstLogin이 false이다")
        void isFirstLogin_isFalseOnSecondLogin() {
            // given
            MockHttpServletRequest mockRequest = new MockHttpServletRequest();
            mockRequest.setRemoteAddr("121.157.114.75");

            authService.adminLogin(createAdminLoginRequest(), TENANT, mockRequest);

            // when
            LoginResult response = authService.adminLogin(createAdminLoginRequest(), TENANT, mockRequest);

            // then
            assertThat(response.getIsFirstLogin()).isFalse();
        }
    }

    @Nested
    @DisplayName("슈퍼 관리자 로그인")
    class SuperAdminLogin {

        @BeforeEach
        void setup() {
            insertUserWithRole(TEST_SUPER_ADMIN_01.toUser(), RoleType.SUPER_ADMIN);
        }

        @Test
        @DisplayName("슈퍼 관리자 로그인에 성공한다")
        void success() {
            // given
            MockHttpServletRequest mockRequest = new MockHttpServletRequest();
            mockRequest.setRemoteAddr("121.157.114.75");
            UserAuthData superAdmin = userQueryRepository.findActiveSuperAdminAuthByUserId(TEST_SUPER_ADMIN_01.getUserId());

            // when
            LoginResult response = authService.superAdminLogin(createSuperAdminLoginRequest(), mockRequest);

            // then
            Assertions.assertAll(
                    () -> assertThat(response.getUserSeq()).isEqualTo(superAdmin.getUserSeq()),
                    () -> assertThat(response.getUserId()).isEqualTo(superAdmin.getUserId()),
                    () -> assertThat(response.getRoles()).contains(RoleType.SUPER_ADMIN),
                    () -> assertThat(jwtProvider.getId(response.getAccessToken())).isEqualTo(superAdmin.getUserSeq()),
                    () -> assertThat(response.getHasChangedPassword()).isFalse()
            );
        }

        @Test
        @DisplayName("잘못된 비밀번호로 로그인하면 LOGIN_FAILED가 발생한다")
        void throwExceptionByWrongPassword() {
            // given
            MockHttpServletRequest mockRequest = new MockHttpServletRequest();
            mockRequest.setRemoteAddr("121.157.114.75");

            // when - then
            assertThatThrownBy(() -> authService.superAdminLogin(
                    new LoginDto(TEST_SUPER_ADMIN_01.getUserId(), "wrong" + TEST_SUPER_ADMIN_01.getEncPwd()), mockRequest))
                    .isInstanceOf(ApiRuntimeException.class)
                    .hasMessage(AuthErrorCode.LOGIN_FAILED.getMessage());
        }
    }

    @Nested
    @DisplayName("진입 키로 로그인 (loginWithEntryKey)")
    class LoginWithEntryKey {

        private UserAuthData superAdmin;
        private MockHttpServletRequest mockRequest;

        @BeforeEach
        void setup() {
            insertUserWithRole(TEST_SUPER_ADMIN_01.toUser(), RoleType.SUPER_ADMIN);
            superAdmin = userQueryRepository.findActiveSuperAdminAuthByUserId(TEST_SUPER_ADMIN_01.getUserId());
            mockRequest = new MockHttpServletRequest();
            mockRequest.setRemoteAddr("127.0.0.1");
        }

        @Test
        @DisplayName("유효한 임시 키로 로그인 성공 시 슈퍼어드민의 LoginResult가 반환된다")
        void success() {
            // given
            String tempKey = tenantAccessTokenService.issue(superAdmin.getUserSeq(), TENANT);

            // when
            LoginResult response = authService.loginWithEntryKey(tempKey, mockRequest);

            // then
            assertThat(response.getUserSeq()).isEqualTo(superAdmin.getUserSeq());
            assertThat(response.getUserId()).isEqualTo(superAdmin.getUserId());
            assertThat(response.getAccessToken()).isNotBlank();
            assertThat(response.getRefreshToken()).isNotBlank();
            assertThat(jwtProvider.getId(response.getAccessToken())).isEqualTo(superAdmin.getUserSeq());
            assertThat(response.getHasChangedPassword()).isFalse();
        }

        @Test
        @DisplayName("임시 키 로그인 성공 후 토큰은 소비되어 재사용할 수 없다")
        void tempKeyIsConsumedAfterLogin() {
            // given
            String tempKey = tenantAccessTokenService.issue(superAdmin.getUserSeq(), TENANT);
            authService.loginWithEntryKey(tempKey, mockRequest);

            // when - then
            assertThatThrownBy(() -> authService.loginWithEntryKey(tempKey, mockRequest))
                    .isInstanceOf(ApiRuntimeException.class)
                    .hasMessage(SuperAdminErrorCode.INVALID_TENANT_ACCESS_TOKEN.getMessage());
        }

        @Test
        @DisplayName("존재하지 않는 임시 키로 로그인하면 INVALID_TENANT_ACCESS_TOKEN 예외가 발생한다")
        void throwExceptionByInvalidTempKey() {
            assertThatThrownBy(() -> authService.loginWithEntryKey("invalidkey", mockRequest))
                    .isInstanceOf(ApiRuntimeException.class)
                    .hasMessage(SuperAdminErrorCode.INVALID_TENANT_ACCESS_TOKEN.getMessage());
        }

        @Test
        @DisplayName("만료된 임시 키로 로그인하면 EXPIRED_TENANT_ACCESS_TOKEN 예외가 발생한다")
        void throwExceptionByExpiredTempKey() {
            // given
            String rawToken = "expiredtempkey123456789012345";
            Instant past = Instant.now().minusSeconds(10);
            tenantAccessTokenRepository.save(
                    TenantAccessToken.of(superAdmin.getUserSeq(), TENANT, TokenHasher.hash(rawToken), past, past)
            );

            // when - then
            assertThatThrownBy(() -> authService.loginWithEntryKey(rawToken, mockRequest))
                    .isInstanceOf(ApiRuntimeException.class)
                    .hasMessage(SuperAdminErrorCode.EXPIRED_TENANT_ACCESS_TOKEN.getMessage());
        }
    }

    private LoginDto createLoginRequest() {
        return new LoginDto(TEST_USER_01.getUserId(), TEST_USER_01.getEncPwd());
    }

    private LoginDto createWrongLoginRequest() {
        return new LoginDto(TEST_USER_01.getUserId(), "wrong" + TEST_USER_01.getEncPwd());
    }

    private LoginDto createAdminLoginRequest() {
        return new LoginDto(TEST_ADMIN_01.getUserId(), TEST_ADMIN_01.getEncPwd());
    }

    private LoginDto createWrongAdminLoginRequest() {
        return new LoginDto(TEST_ADMIN_01.getUserId(), "wrong" + TEST_ADMIN_01.getEncPwd());
    }

    private LoginDto createSuperAdminLoginRequest() {
        return new LoginDto(TEST_SUPER_ADMIN_01.getUserId(), TEST_SUPER_ADMIN_01.getEncPwd());
    }
}
