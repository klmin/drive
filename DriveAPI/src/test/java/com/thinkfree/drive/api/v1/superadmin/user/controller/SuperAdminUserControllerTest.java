package com.thinkfree.drive.api.v1.superadmin.user.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.superadmin.user.request.SuperAdminUserSearchRequest;
import com.thinkfree.drive.api.v1.superadmin.user.response.SuperAdminUserSearchResponse;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.util.StringUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled
@WithMockUserCustom(userSeq = 854, userId = "superadmin@thinkfree.com", userName = "슈퍼 관리자", roles = "ROLE_SUPER_ADMIN")
class SuperAdminUserControllerTest extends AbstractMvcTest {

    /** 검색 대상 테넌트 — userSeq 1(ADMIN)·41(USER) 이 속한 테넌트 */
    private static final int TENANT_SEQ = 1;
    /** 존재하지 않는 테넌트 */
    private static final int MISSING_TENANT_SEQ = 99999;
    /** TENANT_SEQ 의 계정 userId 일부 */
    private static final String USER_ID_KEYWORD = "sunkyoung";
    /** 어느 계정에도 걸리지 않는 검색어 */
    private static final String NO_MATCH_KEYWORD = "zzzz-no-match-zzzz";

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/super-admin/tenants");
    }

    private static String searchUrl(int tenantSeq) {
        return StringUtil.buildUrl(tenantSeq, "users", "search");
    }

    @Test
    void search() {
        var request = new SuperAdminUserSearchRequest(USER_ID_KEYWORD, null);

        ApiResponse<List<SuperAdminUserSearchResponse>> response =
                getForObject(searchUrl(TENANT_SEQ), request, new TypeReference<>() {}, status().isOk());

        assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
        assertFalse(response.getData().isEmpty());
    }

    @Test
    void search_returnsAdminAndUserRoleOnly() {
        var request = new SuperAdminUserSearchRequest(USER_ID_KEYWORD, null);

        ApiResponse<List<SuperAdminUserSearchResponse>> response =
                getForObject(searchUrl(TENANT_SEQ), request, new TypeReference<>() {}, status().isOk());

        assertFalse(response.getData().isEmpty());
        response.getData().forEach(user -> {
            assertTrue(user.roles().contains(RoleType.ADMIN) || user.roles().contains(RoleType.USER));
            assertEquals(UserStatus.ACTIVE, user.status());
        });
    }

    @Test
    void search_whenNoMatch_returnsEmptyList() {
        var request = new SuperAdminUserSearchRequest(NO_MATCH_KEYWORD, null);

        ApiResponse<List<SuperAdminUserSearchResponse>> response =
                getForObject(searchUrl(TENANT_SEQ), request, new TypeReference<>() {}, status().isOk());

        assertNotNull(response.getData());
        assertTrue(response.getData().isEmpty());
    }

    @Test
    void search_whenTenantNotExists_returnsNotFound() {
        var request = new SuperAdminUserSearchRequest(USER_ID_KEYWORD, null);

        ApiResponse<Void> response =
                getForObject(searchUrl(MISSING_TENANT_SEQ), request, new TypeReference<>() {}, status().isNotFound());

        assertFalse(response.isResult());
    }

    @Test
    void search_whenRoleTypeIsGiven_returnsOnlyThatRole() {
        var request = new SuperAdminUserSearchRequest(USER_ID_KEYWORD, RoleType.USER);

        ApiResponse<List<SuperAdminUserSearchResponse>> response =
                getForObject(searchUrl(TENANT_SEQ), request, new TypeReference<>() {}, status().isOk());

        assertFalse(response.getData().isEmpty());
        response.getData().forEach(user -> assertTrue(user.roles().contains(RoleType.USER)));
    }

    @Test
    void search_whenRoleTypeIsSuperAdmin_returnsBadRequest() {
        var request = new SuperAdminUserSearchRequest(USER_ID_KEYWORD, RoleType.SUPER_ADMIN);

        ApiResponse<Void> response =
                getForObject(searchUrl(TENANT_SEQ), request, new TypeReference<>() {}, status().isBadRequest());

        assertFalse(response.isResult());
    }

    @Test
    void search_whenKeywordIsBlank_returnsBadRequest() {
        var request = new SuperAdminUserSearchRequest("  ", null);

        ApiResponse<Void> response =
                getForObject(searchUrl(TENANT_SEQ), request, new TypeReference<>() {}, status().isBadRequest());

        assertFalse(response.isResult());
    }
}
