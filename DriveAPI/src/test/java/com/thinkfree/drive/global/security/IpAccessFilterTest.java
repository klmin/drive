package com.thinkfree.drive.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.access.dto.IpAccessCheckCommand;
import com.thinkfree.drive.access.exception.AccessErrorCode;
import com.thinkfree.drive.access.service.IpAccessControlService;
import com.thinkfree.drive.global.enums.AuthSource;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.superadmin.security.SuperAdminIpGuard;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.user.enums.RoleType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IpAccessFilter 단위 테스트")
class IpAccessFilterTest {

    private static final int TENANT = 1;
    private static final String CLIENT_IP = "203.0.113.5";

    @Mock
    private SuperAdminIpGuard superAdminIpGuard;

    @Mock
    private IpAccessControlService ipAccessControlService;

    private IpAccessFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new IpAccessFilter(superAdminIpGuard, ipAccessControlService, new ObjectMapper());
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        request.setRemoteAddr(CLIENT_IP); // 프록시 뒤 실제 IP 는 RemoteIpValve 가 remoteAddr 로 넣어 준다
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void setAuthentication(RoleType roleType) {
        setAuthentication(roleType, AuthSource.INTERNAL);
    }

    private void setAuthentication(RoleType roleType, AuthSource authSource) {
        UserDetailDto dto = new UserDetailDto(
                100,
                "tester",
                "tester",
                roleType.getAuthority(),
                authSource,
                TENANT,
                java.util.Set.of(),
                java.util.Set.of(roleType)
        );
        CustomUserDetails details = new CustomUserDetails(dto);
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(details, "", details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    @Nested
    @DisplayName("shouldNotFilter - IP_WHITELIST 경로")
    class ShouldNotFilter {

        @Test
        @DisplayName("/api/v1/branding → 필터 skip")
        void brandingPath_skipsFilter() throws Exception {
            
            setPath(request, "/api/v1/branding");

            filter.doFilter(request, response, chain);

            verifyNoInteractions(superAdminIpGuard);
            verifyNoInteractions(ipAccessControlService);
            assertThat(chain.getRequest()).isNotNull();
        }

        @Test
        @DisplayName("/api/v1/auth/login-config → 필터 skip")
        void loginConfigPath_skipsFilter() throws Exception {

            setPath(request, "/api/v1/auth/login-config");

            filter.doFilter(request, response, chain);

            verifyNoInteractions(superAdminIpGuard);
            verifyNoInteractions(ipAccessControlService);
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = {
                "/api/v1/tfo-http-storage/v1/documents/4-123/report.docx/info"
        })
        @DisplayName("오피스 어댑터 콜백은 사용자 IP 검사를 건너뛴다")
        void webOfficePath_skipsFilter(String path) throws Exception {

            setPath(request, path);

            filter.doFilter(request, response, chain);

            verifyNoInteractions(superAdminIpGuard);
            verifyNoInteractions(ipAccessControlService);
            assertThat(chain.getRequest()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Path 기반 role 결정 - 로그인 진입점")
    class PathBasedRoleResolution {

        @Test
        @DisplayName("SUPER_ADMIN 로그인 path → superAdminIpGuard 호출")
        void superAdminLogin_callsSuperAdminGuard() throws Exception {
            request.setRequestURI(SecurityPaths.SUPER_ADMIN_LOGIN);

            filter.doFilter(request, response, chain);

            verify(superAdminIpGuard).checkAllowed(CLIENT_IP);
            verifyNoInteractions(ipAccessControlService);
            assertThat(chain.getRequest()).isNotNull();
        }

        @Test
        @DisplayName("ADMIN 로그인 path → ipAccessControlService 호출 (ADMIN role)")
        void adminLogin_callsIpAccessControlServiceWithAdmin() throws Exception {
            request.setRequestURI(SecurityPaths.ADMIN_LOGIN);

            filter.doFilter(request, response, chain);

            ArgumentCaptor<IpAccessCheckCommand> captor = ArgumentCaptor.forClass(IpAccessCheckCommand.class);
            verify(ipAccessControlService).checkAllowed(captor.capture());
            assertThat(captor.getValue().roleType()).isEqualTo(RoleType.ADMIN);
            assertThat(captor.getValue().clientIp()).isEqualTo(CLIENT_IP);
            assertThat(captor.getValue().tenantSeq()).isEqualTo(TENANT);
            verifyNoInteractions(superAdminIpGuard);
        }

        @Test
        @DisplayName("USER 로그인 path → ipAccessControlService 호출 (USER role)")
        void userLogin_callsIpAccessControlServiceWithUser() throws Exception {
            request.setRequestURI(SecurityPaths.USER_LOGIN);

            filter.doFilter(request, response, chain);

            ArgumentCaptor<IpAccessCheckCommand> captor = ArgumentCaptor.forClass(IpAccessCheckCommand.class);
            verify(ipAccessControlService).checkAllowed(captor.capture());
            assertThat(captor.getValue().roleType()).isEqualTo(RoleType.USER);
            verifyNoInteractions(superAdminIpGuard);
        }
    }

    @Nested
    @DisplayName("SecurityContext 기반 role 결정 - 인증된 일반 요청")
    class AuthenticatedRoleResolution {

        @Test
        @DisplayName("USER 인증 → ipAccessControlService 호출 (USER role)")
        void authenticatedUser_callsIpAccessControlServiceWithUser() throws Exception {
            request.setRequestURI("/api/v1/users/me");
            setAuthentication(RoleType.USER);

            filter.doFilter(request, response, chain);

            ArgumentCaptor<IpAccessCheckCommand> captor = ArgumentCaptor.forClass(IpAccessCheckCommand.class);
            verify(ipAccessControlService).checkAllowed(captor.capture());
            assertThat(captor.getValue().roleType()).isEqualTo(RoleType.USER);
            verifyNoInteractions(superAdminIpGuard);
        }

        @Test
        @DisplayName("ADMIN 인증 → ipAccessControlService 호출 (ADMIN role)")
        void authenticatedAdmin_callsIpAccessControlServiceWithAdmin() throws Exception {
            request.setRequestURI("/api/v1/admins/users");
            setAuthentication(RoleType.ADMIN);

            filter.doFilter(request, response, chain);

            ArgumentCaptor<IpAccessCheckCommand> captor = ArgumentCaptor.forClass(IpAccessCheckCommand.class);
            verify(ipAccessControlService).checkAllowed(captor.capture());
            assertThat(captor.getValue().roleType()).isEqualTo(RoleType.ADMIN);
            verifyNoInteractions(superAdminIpGuard);
        }

        @Test
        @DisplayName("SUPER_ADMIN 인증 → superAdminIpGuard 호출")
        void authenticatedSuperAdmin_callsSuperAdminGuard() throws Exception {
            request.setRequestURI("/api/v1/super-admin/some-resource");
            setAuthentication(RoleType.SUPER_ADMIN);

            filter.doFilter(request, response, chain);

            verify(superAdminIpGuard).checkAllowed(CLIENT_IP);
            verifyNoInteractions(ipAccessControlService);
        }
    }

    @Nested
    @DisplayName("role 결정 실패 - 검증 skip")
    class NoRoleResolved {

        @Test
        @DisplayName("인증 없음 + 로그인 path 아님 → 모든 가드 호출 안 함, 체인 통과")
        void noAuth_nonLoginPath_passesThrough() throws Exception {
            request.setRequestURI("/api/v1/users/me");

            filter.doFilter(request, response, chain);

            verifyNoInteractions(superAdminIpGuard);
            verifyNoInteractions(ipAccessControlService);
            assertThat(chain.getRequest()).isNotNull();
        }

        @Test
        @DisplayName("USER API Key 인증 → IP 정책 미적용, 체인 통과")
        void userApiKey_passesThrough() throws Exception {
            request.setRequestURI("/api/v1/users/me");
            setAuthentication(RoleType.USER, AuthSource.EXTERNAL);

            filter.doFilter(request, response, chain);

            verifyNoInteractions(superAdminIpGuard);
            verifyNoInteractions(ipAccessControlService);
            assertThat(chain.getRequest()).isNotNull();
        }

        @Test
        @DisplayName("ADMIN API Key 인증 → IP 정책 미적용, 체인 통과")
        void adminApiKey_passesThrough() throws Exception {
            request.setRequestURI("/api/v1/admins/users");
            setAuthentication(RoleType.ADMIN, AuthSource.EXTERNAL);

            filter.doFilter(request, response, chain);

            verifyNoInteractions(superAdminIpGuard);
            verifyNoInteractions(ipAccessControlService);
            assertThat(chain.getRequest()).isNotNull();
        }

        @Test
        @DisplayName("principal이 CustomUserDetails가 아니면 → 통과")
        void principalNotCustomUserDetails_passesThrough() throws Exception {
            request.setRequestURI("/api/v1/users/me");
            UsernamePasswordAuthenticationToken token =
                    new UsernamePasswordAuthenticationToken("plain-string-principal", "", List.of());
            SecurityContextHolder.getContext().setAuthentication(token);

            filter.doFilter(request, response, chain);

            verifyNoInteractions(superAdminIpGuard);
            verifyNoInteractions(ipAccessControlService);
        }

        @Test
        @DisplayName("authority가 RoleType에 매핑되지 않으면 (fromAuthority null) → 통과")
        void unknownAuthority_passesThrough() throws Exception {
            request.setRequestURI("/api/v1/users/me");
            UserDetailDto dto = new UserDetailDto(100, "tester", "tester",
                    "ROLE_UNKNOWN_VALUE", AuthSource.INTERNAL, TENANT);
            CustomUserDetails details = new CustomUserDetails(dto);
            UsernamePasswordAuthenticationToken token =
                    new UsernamePasswordAuthenticationToken(details, "", details.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(token);

            filter.doFilter(request, response, chain);

            verifyNoInteractions(superAdminIpGuard);
            verifyNoInteractions(ipAccessControlService);
        }
    }

    @Nested
    @DisplayName("tenant-less 요청 - TenantContext 없음")
    class TenantLessRequest {

        @Test
        @DisplayName("ADMIN 인증 + TenantContext 없음(슈퍼어드민 API 경로) → 500 대신 통과, 테넌트 IP 정책 미호출")
        void authenticatedAdmin_withoutTenant_passesThrough() throws Exception {
            TenantContext.clear();
            setAuthentication(RoleType.ADMIN);
            request.setRequestURI("/api/v1/super-admin/tenants");

            filter.doFilter(request, response, chain);

            verifyNoInteractions(ipAccessControlService, superAdminIpGuard);
            assertThat(chain.getRequest()).isNotNull();
        }

        @Test
        @DisplayName("SUPER_ADMIN 인증 + TenantContext 없음 → 프로퍼티 가드는 그대로 수행")
        void authenticatedSuperAdmin_withoutTenant_stillGuarded() throws Exception {
            TenantContext.clear();
            setAuthentication(RoleType.SUPER_ADMIN);
            request.setRequestURI("/api/v1/super-admin/tenants");

            filter.doFilter(request, response, chain);

            verify(superAdminIpGuard).checkAllowed(CLIENT_IP);
            verifyNoInteractions(ipAccessControlService);
        }
    }

    @Nested
    @DisplayName("ApiRuntimeException 처리 - 차단 응답")
    class ExceptionHandling {

        @Test
        @DisplayName("SuperAdminIpGuard가 BLOCKED_IP 던지면 → 403 응답, 체인 호출 안 함")
        void superAdminGuardThrows_writesErrorResponse() throws Exception {
            request.setRequestURI(SecurityPaths.SUPER_ADMIN_LOGIN);
            willThrow(ApiRuntimeException.type(AccessErrorCode.BLOCKED_IP))
                    .given(superAdminIpGuard).checkAllowed(anyString());

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(AccessErrorCode.BLOCKED_IP.getStatus().value());
            // 체인이 호출되지 않았는지 확인 (MockFilterChain의 request가 null)
            assertThat(chain.getRequest()).isNull();
        }

        @Test
        @DisplayName("IpAccessControlService가 BLOCKED_COUNTRY 던지면 → 403 응답, 체인 호출 안 함")
        void ipAccessControlServiceThrows_writesErrorResponse() throws Exception {
            request.setRequestURI(SecurityPaths.USER_LOGIN);
            willThrow(ApiRuntimeException.type(AccessErrorCode.BLOCKED_COUNTRY))
                    .given(ipAccessControlService).checkAllowed(any(IpAccessCheckCommand.class));

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(AccessErrorCode.BLOCKED_COUNTRY.getStatus().value());
            assertThat(chain.getRequest()).isNull();
        }

        @Test
        @DisplayName("정상 흐름 - 가드가 통과시키면 → 체인 호출")
        void allowed_chainProceeds() throws Exception {
            request.setRequestURI(SecurityPaths.USER_LOGIN);
            // ipAccessControlService.checkAllowed는 기본적으로 아무것도 안 던짐 (void mock)

            filter.doFilter(request, response, chain);

            then(ipAccessControlService).should().checkAllowed(any(IpAccessCheckCommand.class));
            then(superAdminIpGuard).should(never()).checkAllowed(anyString());
            assertThat(chain.getRequest()).isNotNull();
        }
    }
    
    private void setPath(MockHttpServletRequest req, String path) {
        req.setRequestURI(path);
        req.setServletPath(path);
    }

}
