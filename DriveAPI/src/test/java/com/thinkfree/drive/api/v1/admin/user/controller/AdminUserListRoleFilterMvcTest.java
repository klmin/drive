package com.thinkfree.drive.api.v1.admin.user.controller;

import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.user.exception.UserErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계정 목록 통합(⑦-1) — {@code GET /admins/users} 의 역할 필터 rt(보유 기준, 쉼표 구분)와 응답 roles,
 * 삭제된 {@code GET /admins}. dev DB(기본 테스트 호스트 테넌트)의 기존 계정으로 확인한다.
 */
@DisplayName("계정 목록 — rt 역할 필터·roles 응답")
@WithMockUserCustom(roles = "ROLE_ADMIN")
class AdminUserListRoleFilterMvcTest extends AbstractMvcTest {

    private static final String LIST = "/api/v1/admins/users";

    @BeforeEach
    void pinTestHost() {
        setTestHost(DEFAULT_TEST_HOST);
    }

    @Test
    @DisplayName("rt=ADMIN — 항목 전부 roles 에 ADMIN, roleType·isActive 필드 없음")
    void adminFilter() throws Exception {
        mockMvc.perform(withHost(MockMvcRequestBuilders.get(LIST).param("rt", "ADMIN").param("ps", "100")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", not(empty())))
                .andExpect(jsonPath("$.data[*].roles", everyItem(hasItem("ADMIN"))))
                .andExpect(jsonPath("$.data[0].roleType").doesNotExist())
                .andExpect(jsonPath("$.data[0].isActive").doesNotExist());
    }

    @Test
    @DisplayName("rt=USER — 항목 전부 roles 에 USER")
    void userFilter() throws Exception {
        mockMvc.perform(withHost(MockMvcRequestBuilders.get(LIST).param("rt", "USER").param("ps", "100")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", not(empty())))
                .andExpect(jsonPath("$.data[*].roles", everyItem(hasItem("USER"))));
    }

    @Test
    @DisplayName("rt 생략·rt=USER,ADMIN — 사용자·관리자 모두(관리자 포함 확인)")
    void allRoles() throws Exception {
        mockMvc.perform(withHost(MockMvcRequestBuilders.get(LIST).param("ps", "1000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].roles[*]", hasItem("ADMIN")))
                .andExpect(jsonPath("$.data[*].roles[*]", hasItem("USER")));
        mockMvc.perform(withHost(MockMvcRequestBuilders.get(LIST).param("rt", "USER,ADMIN").param("ps", "1000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].roles[*]", hasItem("ADMIN")))
                .andExpect(jsonPath("$.data[*].roles[*]", hasItem("USER")));
    }

    @Test
    @DisplayName("rt=SUPER_ADMIN·ANONYMOUS — 400 INVALID_ROLE_PARAM")
    void invalidRole() throws Exception {
        for (String rt : new String[]{"SUPER_ADMIN", "ANONYMOUS", "USER,SUPER_ADMIN"}) {
            mockMvc.perform(withHost(MockMvcRequestBuilders.get(LIST).param("rt", rt).param("ps", "10")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(UserErrorCode.INVALID_ROLE_PARAM.getErrorCode()));
        }
    }

    @Test
    @DisplayName("GET /admins(관리자 목록) 삭제 — 핸들러 없음")
    void adminListRemoved() throws Exception {
        mockMvc.perform(withHost(MockMvcRequestBuilders.get("/api/v1/admins")))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertTrue(
                        result.getResponse().getStatus() == 404 || result.getResponse().getStatus() == 405,
                        "GET /admins 는 404·405 여야 한다: " + result.getResponse().getStatus()));
    }
}
