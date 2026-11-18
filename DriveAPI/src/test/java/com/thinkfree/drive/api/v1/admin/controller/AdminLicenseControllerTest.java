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

@Disabled
@WithMockUserCustom(tenantSeq = 8)
class AdminLicenseControllerTest extends AbstractMvcTest {

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/admins");
        setTestHost("test8.drive.thinkfree.com");
    }

    @Test
    //@Transactional
    void registerLicense() {

        String url = StringUtil.buildUrl("license");
        MockMultipartFile licenseFile = createMultipartFileFromClasspath(
                "licenseFile",
                "files/Thinkfree_Production_PERSEAT-100_20251203-20251203.tfl",
                 "application/octet-stream");

        multipartForObject(url, licenseFile, null, new TypeReference<Void>() {}, status().isOk());
    }

    @Test
    void getLicense() {

        String url = StringUtil.buildUrl("license");
        Map<String, Object> response = getForObject(url, null, new TypeReference<>() {}, status().isOk());
        assertNotNull(response);
    }

    // 라이선스 상태 조회(GET /api/v1/config/license-status)는 ConfigControllerTest
}
