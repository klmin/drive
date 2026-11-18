package com.thinkfree.drive.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.global.enums.AuthSource;
import com.thinkfree.drive.global.exception.GlobalErrorCode;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.user.enums.RoleType;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("TenantBoundaryFilter 단위 테스트")
class TenantBoundaryFilterTest {

    private static final int TENANT_A = 10;
    private static final int TENANT_B = 20;
    private static final int USER_SEQ = 100;

    private ObjectMapper objectMapper;
    private TenantBoundaryFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new TenantBoundaryFilter(objectMapper);

        request = new MockHttpServletRequest();
        setPath(request, "/api/v1/resources");
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    /**
     * Spring Security {@link org.springframework.security.web.util.matcher.AntPathRequestMatcher}
     * 는 {@code getServletPath()} 기반으로 매칭하므로 RequestURI 와 ServletPath 를 함께 set 해야
     * shouldNotFilter 가 정상적으로 매처와 일치한다.
     */
    private void setPath(MockHttpServletRequest req, String path) {
        req.setRequestURI(path);
        req.setServletPath(path);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private CustomUserDetails userDetails(Integer tenantSeq, String roleType) {
        return new CustomUserDetails(new UserDetailDto(
                USER_SEQ, "user@example.com", "tester", roleType, AuthSource.INTERNAL, tenantSeq
        ));
    }

    private void setAuthenticatedUser(Integer tenantSeq, String roleType) {
        CustomUserDetails details = userDetails(tenantSeq, roleType);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                details, "", List.of(new SimpleGrantedAuthority(roleType))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Nested
    @DisplayName("shouldNotFilter — 필터 자체 면제 경로")
    class SkipPaths {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "/api/v1/auth/login",
                "/api/v1/auth/admin/login",
                "/api/v1/auth/super-admin/login",
                "/api/v1/branding",
                "/api/v1/auth/login-config",
                "/api/v1/files/download"
        })
        @DisplayName("AUTH_WHITELIST 경로는 tenant mismatch 상황에서도 통과 (진짜 skip 검증)")
        void skips_for_auth_whitelist(String path) throws Exception {
            // 일부러 mismatch — skip 동작이 진짜라면 mismatch 무시하고 통과해야 함
            setAuthenticatedUser(TENANT_A, RoleType.USER.getAuthority());
            TenantContext.set(TENANT_B);
            setPath(request, path);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        }

        @Test
        @DisplayName("TENANT_CONSISTENCY_SKIP — 공유 링크(GET)는 mismatch 상황에서도 통과")
        void skips_for_share_link_get() throws Exception {
            setAuthenticatedUser(TENANT_A, RoleType.USER.getAuthority());
            TenantContext.set(TENANT_B);
            request.setMethod("GET");
            setPath(request, "/api/v1/shares/link/some-token");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        }

        @Test
        @DisplayName("TENANT_CONSISTENCY_SKIP 은 GET 에만 적용 — POST + mismatch 면 차단된다")
        void share_link_post_is_not_skipped() throws Exception {
            setAuthenticatedUser(TENANT_A, RoleType.USER.getAuthority());
            TenantContext.set(TENANT_B);
            request.setMethod("POST");
            setPath(request, "/api/v1/shares/link/some-token");

            filter.doFilter(request, response, chain);

            // POST 는 skip 대상 아님 → doFilterInternal 진입 → mismatch 차단
            assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
            verifyNoInteractions(chain);
        }
    }

    @Nested
    @DisplayName("인증 정보 부재/형식 이상 → 통과 (인증 책임은 다른 필터)")
    class UnauthenticatedPasses {

        @Test
        @DisplayName("SecurityContext가 비어있으면 통과한다")
        void passes_when_security_context_empty() throws Exception {
            // SecurityContext 비어있음

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        }

        @Test
        @DisplayName("Anonymous 인증이면 principal이 CustomUserDetails 아님 → 통과")
        void passes_when_anonymous_principal() throws Exception {
            Authentication anon = new AnonymousAuthenticationToken(
                    "key", "anonymousUser",
                    List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
            );
            SecurityContextHolder.getContext().setAuthentication(anon);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("SUPER_ADMIN — 테넌트 검증 면제")
    class SuperAdminBypass {

        @Test
        @DisplayName("SUPER_ADMIN은 tenant 불일치여도 통과한다")
        void super_admin_passes_regardless_of_tenant() throws Exception {
            setAuthenticatedUser(TENANT_A, RoleType.SUPER_ADMIN.getAuthority());
            TenantContext.set(TENANT_B);  // 일부러 mismatch

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("SUPER_ADMIN은 user tenantSeq가 null이어도 통과한다")
        void super_admin_passes_when_tenant_null() throws Exception {
            setAuthenticatedUser(null, RoleType.SUPER_ADMIN.getAuthority());
            TenantContext.set(TENANT_A);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("일반 사용자 — 테넌트 일치 검증")
    class TenantMatch {

        @Test
        @DisplayName("user tenant == request tenant → 통과")
        void user_tenant_matches_request_tenant() throws Exception {
            setAuthenticatedUser(TENANT_A, RoleType.USER.getAuthority());
            TenantContext.set(TENANT_A);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        }

        @Test
        @DisplayName("ADMIN role도 동일 tenant면 통과한다")
        void admin_passes_when_tenant_matches() throws Exception {
            setAuthenticatedUser(TENANT_A, RoleType.ADMIN.getAuthority());
            TenantContext.set(TENANT_A);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("일반 사용자 — 테넌트 불일치 시 차단")
    class TenantMismatch {

        @Test
        @DisplayName("user tenant != request tenant → 403, chain 진입 차단")
        void blocks_when_tenant_mismatch() throws Exception {
            setAuthenticatedUser(TENANT_A, RoleType.USER.getAuthority());
            TenantContext.set(TENANT_B);

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
            verifyNoInteractions(chain);
        }

        @Test
        @DisplayName("user tenantSeq가 null이면 차단된다")
        void blocks_when_user_tenant_null() throws Exception {
            setAuthenticatedUser(null, RoleType.USER.getAuthority());
            TenantContext.set(TENANT_A);

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
            verifyNoInteractions(chain);
        }

        @Test
        @DisplayName("TenantContext가 null이면 차단된다")
        void blocks_when_request_tenant_null() throws Exception {
            setAuthenticatedUser(TENANT_A, RoleType.USER.getAuthority());
            // TenantContext.set 호출 안 함

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
            verifyNoInteractions(chain);
        }

        @Test
        @DisplayName("roleType이 알 수 없는 값이면 SUPER_ADMIN bypass 안 되고 tenant 검증 받는다")
        void unknown_role_does_not_bypass_tenant_check() throws Exception {
            setAuthenticatedUser(TENANT_A, "UNKNOWN_ROLE");
            TenantContext.set(TENANT_B);

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
            verifyNoInteractions(chain);
        }
        
    }

    @Nested
    @DisplayName("403 응답 본문")
    class ForbiddenBody {

        @Test
        @DisplayName("응답은 INVALID_USER errorCode + JSON content-type을 반환한다")
        void writes_invalid_user_error_body() throws Exception {
            setAuthenticatedUser(TENANT_A, RoleType.USER.getAuthority());
            TenantContext.set(TENANT_B);

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
            assertThat(response.getContentType()).contains("application/json");
            assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");

            String body = response.getContentAsString();
            assertThat(body).contains(GlobalErrorCode.INVALID_USER.getErrorCode());
            // tenant 관련 단어가 응답 본문에 노출되면 안 됨 (정보 노출 방지)
            assertThat(body).doesNotContainIgnoringCase("tenant");
        }
    }
}
