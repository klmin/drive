package com.thinkfree.drive.api.v1.admin.user.controller;

import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.quota.exception.QuotaErrorCode;
import com.thinkfree.drive.user.exception.UserErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계정 등록 입력 roles(⑦-3) — multipart 의 쉼표 구분 문자열("USER,ADMIN")이 List&lt;RoleType&gt; 로 바인딩되는지.
 * 전부 저장 전(역할·용량 판정)에서 끝나는 요청만 보낸다 — dev DB 에 계정을 만들지 않는다.
 */
@DisplayName("계정 등록 — roles 입력 바인딩·검증")
@WithMockUserCustom(roles = "ROLE_ADMIN")
class AdminUserRoleInputMvcTest extends AbstractMvcTest {

    private static final String REGISTER = "/api/v1/admins/users";

    @BeforeEach
    void pinTestHost() {
        setTestHost(DEFAULT_TEST_HOST);
    }

    private MockHttpServletRequestBuilder register(String roles, String tq) {
        var builder = MockMvcRequestBuilders.multipart(REGISTER)
                .param("ui", "role-input-mvc@test.com")
                .param("encPwd", "Passw0rd!x")
                .param("un", "roles");
        if (roles != null) builder.param("roles", roles);
        if (tq != null) builder.param("tq", tq);
        return withHost(builder);
    }

    @Test
    @DisplayName("roles 생략 — 400(필수 입력)")
    void rolesMissing() throws Exception {
        mockMvc.perform(register(null, "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("roles=SUPER_ADMIN·ANONYMOUS·USER,SUPER_ADMIN — 400 USER_028")
    void invalidRoles() throws Exception {
        for (String roles : new String[]{"SUPER_ADMIN", "ANONYMOUS", "USER,SUPER_ADMIN"}) {
            mockMvc.perform(register(roles, "1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(UserErrorCode.INVALID_ROLE_PARAM.getErrorCode()));
        }
    }

    @Test
    @DisplayName("roles=USER,ADMIN 은 두 역할로 바인딩 — USER 규칙이라 용량 생략 시 USER_026")
    void userAndAdminBound() throws Exception {
        mockMvc.perform(register("USER,ADMIN", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(UserErrorCode.REQUIRED_FIELD.getErrorCode()));
    }

    @Test
    @DisplayName("roles=ADMIN 에 양수 용량 — QUOTA_009(관리자만 보유는 용량 0)")
    void adminOnlyWithQuota() throws Exception {
        mockMvc.perform(register("ADMIN", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(QuotaErrorCode.ADMIN_QUOTA_NOT_ALLOWED.getErrorCode()));
    }
}
