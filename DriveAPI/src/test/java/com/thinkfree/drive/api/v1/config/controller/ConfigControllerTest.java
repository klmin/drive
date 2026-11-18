package com.thinkfree.drive.api.v1.config.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.systemconfig.response.OfficeResponse;
import com.thinkfree.drive.api.v1.config.response.ClientConfigResponse;
import com.thinkfree.drive.api.v1.config.response.FileSizeLimitResponse;
import com.thinkfree.drive.api.v1.config.response.TenantConfigResponse;
import com.thinkfree.drive.tenant.enums.TenantType;
import com.thinkfree.drive.tenant.enums.TenantUserMode;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@WithMockUserCustom(roles = "ROLE_USER")
class ConfigControllerTest extends AbstractMvcTest {

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/config");
    }

    @Test
    void getFileSizeLimit() {
        ApiResponse<FileSizeLimitResponse> response = getForObject("file-size-limit", null, new TypeReference<>() {}, status().isOk());
        Assertions.assertNotNull(response);
        Assertions.assertEquals(OK.value(), response.getCode());
    }

    @Test
    void getOfficeConfig(){
        ApiResponse<OfficeResponse> response = getForObject("office", null, new TypeReference<>(){}, status().isOk());
        Assertions.assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
    }
    
    @Test
    void getClientConfig(){
        ApiResponse<ClientConfigResponse> response = getForObject(null, null, new TypeReference<>(){}, status().isOk());
        Assertions.assertNotNull(response.getData());
        Assertions.assertNotNull(response.getData().officeStatus());
        assertEquals(OK.value(), response.getCode());
    }

    /** 기본 테넌트(설치 시드)는 SELF_MANAGED + MULTI */
    @Test
    void getTenantConfig() {
        ApiResponse<TenantConfigResponse> response = getForObject("tenant", null, new TypeReference<>(){}, status().isOk());
        assertEquals(OK.value(), response.getCode());
        assertEquals(TenantType.SELF_MANAGED, response.getData().tenantType());
        assertEquals(TenantUserMode.MULTI, response.getData().userMode());
    }

    /** 응답은 ApiResponse 로 감싸지 않은 라이선스 상태 Map (구 GET /api/v1/admins/get/license/status) */
    @Test
    void getLicenseStatus() {
        Map<String, Object> response = getForObject("license-status", null, new TypeReference<>(){}, status().isOk());
        Assertions.assertNotNull(response);
    }
}
