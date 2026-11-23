package com.thinkfree.drive.api.v1.superadmin.basedomain.controller;

import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.superadmin.basedomain.request.SuperAdminBaseDomainUpsertRequest;
import com.thinkfree.drive.api.v1.superadmin.basedomain.response.SuperAdminBaseDomainResponse;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockUserCustom(roles = "ROLE_SUPER_ADMIN")
@Transactional
class SuperAdminBaseDomainControllerTest extends AbstractMvcTest {

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/super-admin/base-domain");
    }

    @Test
    void find() {

        ApiResponse<SuperAdminBaseDomainResponse> response = getForObject(null, null, new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }

    @Test
    void upsert() {

        var request = new SuperAdminBaseDomainUpsertRequest("drive.thinkfree.com");

        ApiResponse<Void> response = putForObject(null, request, new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }
}
