package com.thinkfree.drive.api.v1.config.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.systemconfig.response.OfficeResponse;
import com.thinkfree.drive.api.v1.config.response.ClientConfigResponse;
import com.thinkfree.drive.api.v1.config.response.FileSizeLimitResponse;
import com.thinkfree.drive.api.v1.config.response.TenantConfigResponse;
import com.thinkfree.drive.common.DriveFixtures;
import com.thinkfree.drive.license.exception.ResultCode;
import com.thinkfree.drive.tenant.enums.TenantType;
import com.thinkfree.drive.tenant.enums.TenantUserMode;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@WithMockUserCustom(roles = "ROLE_USER")
class ConfigControllerTest extends AbstractMvcTest {

    @Autowired
    private JdbcTemplate jdbc;

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

    /**
     * 응답은 ApiResponse 로 감싸지 않는다(구 GET /api/v1/admins/get/license/status) — 오류도 {code, error} 그대로.
     * 저장된 라이선스가 검증되지 않으면 400 + LICENSE_FILE_NOT_VALID_FORMAT.
     * <p>
     * 정상(200) 경로는 아직 테스트하지 못한다: 저장소의 .tfl 파일은 모두 운영 공개키 서명 검증에 실패한다
     * (RSA 형식인 TFD_* 도 "서명 검증 실패", 나머지는 예전 AES 형식). 테스트용 서명 라이선스가 생기면 그 파일로 200 을 확인한다.
     */
    @Test
    @Transactional
    void getLicenseStatus() {
        DriveFixtures.of(jdbc).license(DriveFixtures.DEFAULT_TENANT, "files/TFD_Thinkfree_Production_SEAT1000-PWL-AUTH-STATS_20261017-20261029.tfl");

        Map<String, Object> response = getForObject("license-status", null, new TypeReference<>(){}, status().isBadRequest());

        Assertions.assertNotNull(response);
        assertEquals(ResultCode.LICENSE_FILE_NOT_VALID_FORMAT.getCode(), response.get("code"));
        assertEquals(ResultCode.LICENSE_FILE_NOT_VALID_FORMAT.name(), response.get("error"));
    }

    /** 라이선스가 없으면 404 + LICENSE_FILE_NOT_EXIST */
    @Test
    void getLicenseStatus_notRegistered() {
        Map<String, Object> response = getForObject("license-status", null, new TypeReference<>(){}, status().isNotFound());

        assertEquals(ResultCode.LICENSE_FILE_NOT_EXIST.name(), response.get("error"));
    }
}
