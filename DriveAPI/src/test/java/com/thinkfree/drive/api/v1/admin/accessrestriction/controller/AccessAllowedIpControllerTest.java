package com.thinkfree.drive.api.v1.admin.accessrestriction.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.accessrestriction.request.AllowedIpsUpdateRequest;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockUserCustom//(tenantSeq = 3)
class AccessAllowedIpControllerTest extends AbstractMvcTest {

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/admins/access-restriction");
        //changeTestHost();
    }

    //@Transactional
    @Test
    void putAllowedIps() {
        var request = new AllowedIpsUpdateRequest(List.of("203.0.113.0/24", "10.0.0.1/32", "192.168.1.0/24"));
        ApiResponse<Void> response = putForObject("ips", request, new TypeReference<>() {}, status().isOk());
        Assertions.assertEquals(OK.value(), response.getCode());
    }

    @Transactional
    @Test
    void putAllowedIps_빈목록이면_전체삭제() {
        var request = new AllowedIpsUpdateRequest(List.of());
        ApiResponse<Void> response = putForObject("ips", request, new TypeReference<>() {}, status().isOk());
        Assertions.assertEquals(OK.value(), response.getCode());
    }

    @Test
    void putAllowedIps_null이면_400() {
        var request = new AllowedIpsUpdateRequest(null);
        putForObject("ips", request, new TypeReference<>() {}, status().isBadRequest());
    }

    @Test
    void putAllowedIps_유효하지않은CIDR이면_400() {
        var request = new AllowedIpsUpdateRequest(List.of("invalid-ip"));
        putForObject("ips", request, new TypeReference<>() {}, status().isBadRequest());
    }
}
