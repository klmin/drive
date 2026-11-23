package com.thinkfree.drive.api.v1.admin.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.util.StringUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockUserCustom(tenantSeq = 8)
@Transactional
class AdminLicenseControllerTest extends AbstractMvcTest {

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/admins");
        setTestHost("test8.drive.thinkfree.com");
    }

    @Test
    //@Transactional
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void registerLicense() {

        String url = StringUtil.buildUrl("license");
        MockMultipartFile licenseFile = createMultipartFileFromClasspath(
                "licenseFile",
                "files/Thinkfree_Production_PERSEAT-100_20251203-20251203.tfl",
                 "application/octet-stream");

        multipartForObject(url, licenseFile, null, new TypeReference<Void>() {}, status().isOk());
    }

    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void getLicense() {

        String url = StringUtil.buildUrl("license");
        Map<String, Object> response = getForObject(url, null, new TypeReference<>() {}, status().isOk());
        assertNotNull(response);
    }

    // 라이선스 상태 조회(GET /api/v1/config/license-status)는 ConfigControllerTest
}
