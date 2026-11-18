package com.thinkfree.drive.api.v1.admin.user.controller;

import com.jayway.jsonpath.JsonPath;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.config.security.WithMockUserCustom;
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
 * 계정 단건·검색 응답 roles 전환(⑦-2) — roleType·isActive 대신 보유 역할 roles, 검색 rt 는 보유 기준.
 * dev DB(기본 테스트 호스트 테넌트)의 기존 관리자 계정으로 확인한다.
 */
@DisplayName("계정 단건·검색 — roles 응답")
@WithMockUserCustom(roles = "ROLE_ADMIN")
class AdminUserRolesResponseMvcTest extends AbstractMvcTest {

    private static final String BASE = "/api/v1/admins/users";

    @BeforeEach
    void pinTestHost() {
        setTestHost(DEFAULT_TEST_HOST);
    }

    @Test
    @DisplayName("GET /admins/users/{userSeq} — roles(보유 역할), roleType·isActive 필드 없음")
    void detail() throws Exception {
        String list = mockMvc.perform(withHost(MockMvcRequestBuilders.get(BASE).param("rt", "ADMIN").param("ps", "1")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer userSeq = JsonPath.read(list, "$.data[0].userSeq");

        mockMvc.perform(withHost(MockMvcRequestBuilders.get(BASE + "/" + userSeq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userSeq").value(userSeq))
                .andExpect(jsonPath("$.data.roles", hasItem("ADMIN")))
                .andExpect(jsonPath("$.data.roleType").doesNotExist())
                .andExpect(jsonPath("$.data.isActive").doesNotExist());
    }

    @Test
    @DisplayName("GET /admins/users/search rt=ADMIN — 항목 전부 roles 에 ADMIN, roleType 필드 없음")
    void searchAdmin() throws Exception {
        mockMvc.perform(withHost(MockMvcRequestBuilders.get(BASE + "/search").param("sk", "a").param("rt", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", not(empty())))
                .andExpect(jsonPath("$.data[*].roles", everyItem(hasItem("ADMIN"))))
                .andExpect(jsonPath("$.data[0].roleType").doesNotExist());
    }

    @Test
    @DisplayName("GET /admins/users/search rt 생략 — 기존과 같이 USER 보유 계정만")
    void searchDefaultUser() throws Exception {
        mockMvc.perform(withHost(MockMvcRequestBuilders.get(BASE + "/search").param("sk", "a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].roles", everyItem(hasItem("USER"))));
    }
}
