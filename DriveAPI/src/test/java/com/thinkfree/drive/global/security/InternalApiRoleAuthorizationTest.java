package com.thinkfree.drive.global.security;

import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 내부 체인({@code /api/**}) 역할 경로 규칙 — 동작 역할(토큰 role 1개) 기준, 하이어라키는 SUPER_ADMIN > ADMIN 만.
 * <p>
 * 인가는 핸들러보다 먼저라 "허용" 은 <b>403 이 아님</b> 으로 단언한다(핸들러 결과 200·400·500 무관).
 * 거부 코드: 익명 계정 REQUEST_006(기존 AnonymousAccessFilter 응답), 그 외 AUTH_009.
 */
@DisplayName("내부 API 역할 경로 인가 규칙")
class InternalApiRoleAuthorizationTest extends AbstractMvcTest {

    private static final String ADMINS = "/api/v1/admins";
    private static final String SUPER_ADMIN_TENANTS = "/api/v1/super-admin/tenants";
    /** 드라이브(사용자 전용) */
    private static final String QUOTA = "/api/v1/users/quota";
    private static final String TREE = "/api/v1/resources/tree";
    /** 공용 + 익명(@AnonymousAccessible) */
    private static final String ME = "/api/v1/users";
    private static final String CONFIG = "/api/v1/config";
    /** 공용 */
    private static final String LICENSE_STATUS = "/api/v1/config/license-status";
    private static final String USER_EXISTS = "/api/v1/users/exists?userId=x@x.com";
    /** 익명 허용 드라이브(@AnonymousAccessible) */
    private static final String RESOURCE_DETAIL = "/api/v1/resources/1";

    /** {@code AbstractMvcTest.testHost} 는 static 이라 다른 테스트가 바꿔 둔 값이 남는다 — 호스트가 다르면 TenantBoundaryFilter 가 403 */
    @BeforeEach
    void pinTestHost() {
        setTestHost(DEFAULT_TEST_HOST);
    }

    private void expectDenied(MockHttpServletRequestBuilder request, String code) throws Exception {
        mockMvc.perform(withHost(request))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(code));
    }

    /** 슈퍼관리자 경로 — 접두 없는 요청은 테넌트 판별을 건너뛰어 테넌트 사용자는 인가 전에 TenantBoundaryFilter 가 403(REQUEST_006) */
    private void expectForbidden(String url) throws Exception {
        mockMvc.perform(withHost(MockMvcRequestBuilders.get(url)))
                .andExpect(status().isForbidden());
    }

    private void expectDenied(String url) throws Exception {
        expectDenied(MockMvcRequestBuilders.get(url), "AUTH_009");
    }

    private void expectAllowed(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(withHost(request))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertNotEquals(HttpStatus.FORBIDDEN.value(), status, "이 role 로 인가를 통과해야 한다");
                    assertNotEquals(HttpStatus.UNAUTHORIZED.value(), status, "이 role 로 인증돼 있어야 한다");
                });
    }

    private void expectAllowed(String url) throws Exception {
        expectAllowed(MockMvcRequestBuilders.get(url));
    }

    @Nested
    @DisplayName("인증 없음 — 스프링 익명 비활성이라 익명 허용 경로도 401")
    class NoAuth {

        @Test
        @DisplayName("@AnonymousAccessible 경로·공용·드라이브 모두 401")
        void allProtectedPaths_return401() throws Exception {
            for (String url : new String[]{ME, CONFIG, RESOURCE_DETAIL, TREE, LICENSE_STATUS, ADMINS}) {
                mockMvc.perform(withHost(MockMvcRequestBuilders.get(url)))
                        .andExpect(status().isUnauthorized());
            }
        }
    }

    @Nested
    @DisplayName("USER")
    @WithMockUserCustom(roles = "ROLE_USER")
    class UserRole {

        @Test
        @DisplayName("드라이브·공용·라이선스 상태 허용")
        void driveAndCommon_allowed() throws Exception {
            expectAllowed(QUOTA);
            expectAllowed(TREE);
            expectAllowed(ME);
            expectAllowed(CONFIG);
            expectAllowed(LICENSE_STATUS);
            expectAllowed(MockMvcRequestBuilders.patch("/api/v1/users/password"));
        }

        @Test
        @DisplayName("관리자·슈퍼관리자 경로 403 AUTH_009")
        void adminPaths_denied() throws Exception {
            expectDenied(ADMINS);
            expectForbidden(SUPER_ADMIN_TENANTS);
        }
    }

    @Nested
    @DisplayName("ADMIN — ADMIN > USER 하이어라키 없음")
    @WithMockUserCustom(roles = "ROLE_ADMIN")
    class AdminRole {

        @Test
        @DisplayName("관리자 경로·공용(GET/PATCH users, 비밀번호, ID 중복, 로그아웃, config, 라이선스 상태) 허용")
        void adminAndCommon_allowed() throws Exception {
            expectAllowed(ADMINS);
            expectAllowed(ME);
            expectAllowed(CONFIG);
            expectAllowed(LICENSE_STATUS);
            expectAllowed(USER_EXISTS);
            expectAllowed(MockMvcRequestBuilders.patch("/api/v1/users"));
            expectAllowed(MockMvcRequestBuilders.patch("/api/v1/users/password"));
        }

        @Test
        @DisplayName("드라이브 API 403 AUTH_009 — 사용자 화면 로그인(USER 토큰) 필요")
        void drive_denied() throws Exception {
            expectDenied(QUOTA);
            expectDenied(TREE);
            expectDenied("/api/v1/users/profile-image");
            expectDenied(RESOURCE_DETAIL);
        }

        @Test
        @DisplayName("슈퍼관리자 경로 403")
        void superAdmin_denied() throws Exception {
            expectForbidden(SUPER_ADMIN_TENANTS);
        }
    }

    @Nested
    @DisplayName("SUPER_ADMIN — SUPER_ADMIN > ADMIN 상속")
    @WithMockUserCustom(roles = "ROLE_SUPER_ADMIN")
    class SuperAdminRole {

        @Test
        @DisplayName("슈퍼관리자·관리자 경로·공용 허용")
        void allowed() throws Exception {
            expectAllowed(SUPER_ADMIN_TENANTS);
            expectAllowed(ADMINS);
            expectAllowed(ME);
            expectAllowed(MockMvcRequestBuilders.patch("/api/v1/users"));
        }

        @Test
        @DisplayName("드라이브 API 403")
        void drive_denied() throws Exception {
            expectDenied(QUOTA);
            expectDenied(TREE);
        }
    }

    @Nested
    @DisplayName("ANONYMOUS(공유 링크 익명 계정)")
    @WithMockUserCustom(roles = "ROLE_ANONYMOUS")
    class AnonymousRole {

        @Test
        @DisplayName("@AnonymousAccessible 경로(공용 포함) 허용")
        void anonymousAccessible_allowed() throws Exception {
            expectAllowed(ME);
            expectAllowed(CONFIG);
            expectAllowed(RESOURCE_DETAIL);
        }

        @Test
        @DisplayName("그 외 403 REQUEST_006 — 같은 경로의 다른 메서드(PATCH users)도 거부")
        void others_deniedWithRequest006() throws Exception {
            expectDenied(MockMvcRequestBuilders.get(QUOTA), "REQUEST_006");
            expectDenied(MockMvcRequestBuilders.get(LICENSE_STATUS), "REQUEST_006");
            expectDenied(MockMvcRequestBuilders.patch("/api/v1/users"), "REQUEST_006");
            expectDenied(MockMvcRequestBuilders.get(ADMINS), "REQUEST_006");
        }
    }
}
