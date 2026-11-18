package com.thinkfree.drive.api.v1.admin.auditlog.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** governance.audit.enabled=false 면 조회 API 가 등록되지 않아 404 JSON 을 돌려준다 */
@WithMockUserCustom
@TestPropertySource(properties = "governance.audit.enabled=false")
class AuditLogControllerDisabledTest extends AbstractMvcTest {

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/admins/audit-logs");
    }

    @Test
    void find_whenAuditLogDisabled_returnsNotFoundJson() {
        ApiResponse<Void> response = getForObject(null, null, new TypeReference<>() {}, status().isNotFound());

        assertFalse(response.isResult());
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getCode());
    }

    @Test
    void export_whenAuditLogDisabled_returnsNotFoundJson() {
        ApiResponse<Void> response = getForObject("export", null, new TypeReference<>() {}, status().isNotFound());

        assertFalse(response.isResult());
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getCode());
    }
}
