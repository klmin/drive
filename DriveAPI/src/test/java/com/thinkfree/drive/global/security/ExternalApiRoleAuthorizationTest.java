package com.thinkfree.drive.global.security;

import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.global.enums.AuthSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 외부 체인({@code /api/public/**})의 role 경로 규칙 검증.
 * <p>
 * 인가는 핸들러 매핑보다 먼저 일어나므로, 아직 {@code @ExternalApi} 가 붙지 않은 경로도
 * 규칙만은 그대로 적용된다 — 통과하면 404, 막히면 403 이다. 그래서 "허용" 은 404 가 아니라
 * <b>403 이 아님</b> 으로 단언한다. 이후 개방 단계에서 200 이 되어도 테스트가 깨지지 않는다.
 */
@DisplayName("외부 API role 경로 인가 규칙")
class ExternalApiRoleAuthorizationTest extends AbstractMvcTest {

    private static final String ADMINS = "/api/public/v1/admins";
    private static final String ADMIN_API_KEYS = "/api/public/v1/admins/api-keys";
    private static final String LICENSE_STATUS = "/api/public/v1/config/license-status";
    private static final String SUPER_ADMIN_TENANTS = "/api/public/v1/super-admin/tenants";
    private static final String USER_API = "/api/public/v1/users/exists?userId=x@x.com";

    /** {@code AbstractMvcTest.testHost} 는 static 이라 다른 테스트가 바꿔 둔 값이 남는다 —
     *  호스트가 다르면 TenantBoundaryFilter 가 인가 전에 403 을 내므로 매번 고정한다 */
    @BeforeEach
    void pinTestHost() {
        setTestHost(DEFAULT_TEST_HOST);
    }

    private void expectForbidden(String url) throws Exception {
        expectForbidden(MockMvcRequestBuilders.get(url));
    }

    private void expectForbidden(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(withHost(request))
                .andExpect(status().isForbidden());
    }

    private void expectAllowed(String url) throws Exception {
        expectAllowed(MockMvcRequestBuilders.get(url));
    }

    private void expectAllowed(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(withHost(request))
                .andExpect(result -> assertNotEquals(
                        HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus(),
                        "이 role 로 인가를 통과해야 한다"));
    }

    @Nested
    @DisplayName("인증 없음 — 스프링 익명 비활성")
    class NoAuth {

        @Test
        @DisplayName("사용자·관리자·라이선스 상태 경로 → 401")
        void allPaths_return401() throws Exception {
            for (String url : new String[]{USER_API, ADMINS, LICENSE_STATUS}) {
                mockMvc.perform(withHost(MockMvcRequestBuilders.get(url)))
                        .andExpect(status().isUnauthorized());
            }
        }
    }

    @Nested
    @DisplayName("USER role 키")
    @WithMockUserCustom(roles = "ROLE_USER", authSource = AuthSource.EXTERNAL)
    class UserRole {

        @Test
        @DisplayName("관리자 경로 → 403")
        void admins_returns403() throws Exception {
            expectForbidden(ADMINS);
        }

        @Test
        @DisplayName("관리자 API Key 경로 → 403")
        void adminApiKeys_returns403() throws Exception {
            expectForbidden(ADMIN_API_KEYS);
        }

        @Test
        @DisplayName("슈퍼관리자 경로 → 403")
        void superAdmin_returns403() throws Exception {
            expectForbidden(SUPER_ADMIN_TENANTS);
        }

        @Test
        @DisplayName("라이선스 상태 조회(config) 는 USER 이상이면 허용")
        void licenseStatus_isAllowed() throws Exception {
            expectAllowed(LICENSE_STATUS);
        }
    }

    @Nested
    @DisplayName("ADMIN role 키")
    @WithMockUserCustom(roles = "ROLE_ADMIN", authSource = AuthSource.EXTERNAL)
    class AdminRole {

        @Test
        @DisplayName("관리자 경로 → 허용")
        void admins_isAllowed() throws Exception {
            expectAllowed(ADMINS);
        }

        @Test
        @DisplayName("관리자 API Key 경로 → 허용")
        void adminApiKeys_isAllowed() throws Exception {
            expectAllowed(ADMIN_API_KEYS);
        }

        @Test
        @DisplayName("슈퍼관리자 경로 → 403")
        void superAdmin_returns403() throws Exception {
            expectForbidden(SUPER_ADMIN_TENANTS);
        }

        @Test
        @DisplayName("라이선스 상태 조회 → 허용(ADMIN 명시 — ADMIN > USER 하이어라키 없음)")
        void licenseStatus_isAllowed() throws Exception {
            expectAllowed(LICENSE_STATUS);
        }
    }

    @Nested
    @DisplayName("ADMIN role 키(scope 있음) — 사용자 API 는 USER 키만")
    @WithMockUserCustom(roles = "ROLE_ADMIN", authSource = AuthSource.EXTERNAL, scopes = {ApiKeyScope.READ, ApiKeyScope.WRITE})
    class AdminRoleWithScope {

        @Test
        @DisplayName("외부 사용자 API → 403")
        void userApi_returns403() throws Exception {
            expectForbidden(USER_API);
        }
    }

    @Nested
    @DisplayName("USER role 키(scope 있음)")
    @WithMockUserCustom(roles = "ROLE_USER", authSource = AuthSource.EXTERNAL, scopes = {ApiKeyScope.READ, ApiKeyScope.WRITE})
    class UserRoleWithScope {

        @Test
        @DisplayName("외부 사용자 API → 인가 통과")
        void userApi_isAllowed() throws Exception {
            expectAllowed(USER_API);
        }
    }

    @Nested
    @DisplayName("USER role 키(scope 없음) — scope 게이트")
    @WithMockUserCustom(roles = "ROLE_USER", authSource = AuthSource.EXTERNAL)
    class UserRoleWithoutScope {

        @Test
        @DisplayName("외부 사용자 API → 403")
        void userApi_returns403() throws Exception {
            expectForbidden(USER_API);
        }
    }

    @Nested
    @DisplayName("SUPER_ADMIN role 키")
    @WithMockUserCustom(roles = "ROLE_SUPER_ADMIN", authSource = AuthSource.EXTERNAL)
    class SuperAdminRole {

        @Test
        @DisplayName("슈퍼관리자 경로 → 허용")
        void superAdmin_isAllowed() throws Exception {
            expectAllowed(SUPER_ADMIN_TENANTS);
        }

        @Test
        @DisplayName("관리자 경로는 권한 상속으로 허용")
        void admins_isAllowed() throws Exception {
            expectAllowed(ADMINS);
        }

        /** 발급(POST)만 AdminApiKeyController 가드로 막힌다 — 경로 자체는 권한 상속으로 열려 있다 */
        @Test
        @DisplayName("관리자 API Key 경로 → 허용 (발급 차단은 컨트롤러 가드 몫)")
        void adminApiKeys_isAllowed() throws Exception {
            expectAllowed(ADMIN_API_KEYS);
        }
    }
}
