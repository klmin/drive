package com.thinkfree.drive.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("외부 API role 규칙 표")
class ExternalApiRoleRulesTest {

    @Test
    @DisplayName("관리자 경로는 ADMIN, 템플릿 변수가 있어도 같다")
    void adminPaths() {
        assertEquals(List.of("ADMIN"), ExternalApiRoleRules.rolesFor("/api/public/v1/admins/users"));
        assertEquals(List.of("ADMIN"), ExternalApiRoleRules.rolesFor("/api/public/v1/admins/users/{userSeq}/password"));
    }

    @Test
    @DisplayName("라이선스 상태는 USER·ADMIN 공용 — 관리자 규칙보다 먼저 본다")
    void licenseStatus() {
        assertEquals(List.of("USER", "ADMIN"), ExternalApiRoleRules.rolesFor("/api/public/v1/config/license-status"));
    }

    @Test
    @DisplayName("그 외 외부 API 는 USER 만 — config 하위 다른 경로도 마찬가지")
    void userPaths() {
        assertEquals(List.of("USER"), ExternalApiRoleRules.rolesFor("/api/public/v1/resources"));
        assertEquals(List.of("USER"), ExternalApiRoleRules.rolesFor("/api/public/v1/users/api-keys"));
        assertEquals(List.of("USER"), ExternalApiRoleRules.rolesFor("/api/public/v1/config/office"));
    }

    @Test
    @DisplayName("슈퍼관리자 경로는 SUPER_ADMIN")
    void superAdminPaths() {
        assertEquals(List.of("SUPER_ADMIN"), ExternalApiRoleRules.rolesFor("/api/public/v1/super-admin/tenants"));
    }

    @Test
    @DisplayName("관리자 경로 접두사만 같은 경로(/adminsX)는 관리자 규칙에 걸리지 않는다")
    void prefixLookalike() {
        assertEquals(List.of("USER"), ExternalApiRoleRules.rolesFor("/api/public/v1/adminsX"));
    }
}
