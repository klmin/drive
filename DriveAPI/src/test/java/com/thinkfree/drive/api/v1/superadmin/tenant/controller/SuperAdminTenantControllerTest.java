package com.thinkfree.drive.api.v1.superadmin.tenant.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.superadmin.tenant.request.SuperAdminTenantCreateRequest;
import com.thinkfree.drive.api.v1.superadmin.tenant.request.SuperAdminTenantUpdateRequest;
import com.thinkfree.drive.api.v1.superadmin.tenant.response.SuperAdminTenantDetailResponse;
import com.thinkfree.drive.api.v1.superadmin.tenant.response.SuperAdminTenantListResponse;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.util.StringUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled
@WithMockUserCustom(roles = "ROLE_SUPER_ADMIN")
class SuperAdminTenantControllerTest extends AbstractMvcTest {

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/super-admin/tenants");
    }
    
    @Test
    void list() {

        Integer ps = 20;
        Long ci = null;
        Object cv = null;

        var cursorPagingRequest = new CursorPagingRequest(cv, ci, null, ps);
        ApiResponse<List<SuperAdminTenantListResponse>> response = getForObject(null, cursorPagingRequest, new TypeReference<>() {}, status().isOk());

        assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }
    
    @Test
    void find() {

        Integer tenantSeq = 27;

        ApiResponse<SuperAdminTenantDetailResponse> response = getForObject(tenantSeq.toString(), null, new TypeReference<>() {}, status().isOk());

        assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
        assertEquals(tenantSeq, response.getData().tenantSeq());
        assertNotNull(response.getData().tenantName());
        assertNotNull(response.getData().subdomain());
        assertNotNull(response.getData().allowedIps());
    }

    @Test
    void find_whenTenantNotExists_returnsNotFound() {

        Integer tenantSeq = 999999;

        ApiResponse<SuperAdminTenantDetailResponse> response = getForObject(tenantSeq.toString(), null, new TypeReference<>() {}, status().isNotFound());

        assertEquals(HttpStatus.NOT_FOUND.value(), response.getCode());
    }

    @Test
    //@Transactional
    void create() {
        var request = new SuperAdminTenantCreateRequest(
                "테스트 테넌트20111",
                "test-2011",
                1,
                "admin-test2011@thinkfree.com",
                "관리자",
                "Test1234!",
                1073741824L, // 1GB
                List.of(), null, null
        );

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isCreated());

        assertEquals(CREATED.value(), response.getCode());
    }

    @Test
    //@Transactional
    void create_withAllowedIps() {
        var request = new SuperAdminTenantCreateRequest(
                "test16",
                "test16",
                2,
                "admin-test556@thinkfree.com",
                "관리자",
                "Test1234!",
                1073741824L, // 1GB
                List.of("192.168.0.12", "192.168.0.0/24"), null, null
        );

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isCreated());

        assertEquals(CREATED.value(), response.getCode());
    }

    @Test
    void create_whenTenantNameIsBlank_returnsBadRequest() {
        var request = new SuperAdminTenantCreateRequest(
                "  ",
                "test-blank",
                1,
                "admin@thinkfree.com",
                "관리자",
                "Test1234!",
                1073741824L, // 1GB
                List.of(), null, null
        );

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isBadRequest());

        assertEquals(BAD_REQUEST.value(), response.getCode());
    }

    @Test
    void create_whenDomainIsBlank_returnsBadRequest() {
        var request = new SuperAdminTenantCreateRequest(
                "test-domain-blank",
                "  ",
                1,
                "admin@thinkfree.com",
                "관리자",
                "Test1234!",
                1073741824L, // 1GB
                List.of(), null, null
        );

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isBadRequest());

        assertEquals(BAD_REQUEST.value(), response.getCode());
    }

    @Test
    void create_whenDomainInvalid_returnsBadRequest() {
        // 풀 도메인 정규식은 컨트롤러가 아닌 service 의 DomainValidator 에서 검증된다.
        // 따라서 응답코드는 동일하게 400 (INVALID_TENANT_DOMAIN).
        var request = new SuperAdminTenantCreateRequest(
                "test-domain-invalid",
                "Invalid_Domain!",
                1,
                "admin@thinkfree.com",
                "관리자",
                "Test1234!",
                1073741824L, // 1GB
                List.of(), null, null
        );

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isBadRequest());

        assertEquals(BAD_REQUEST.value(), response.getCode());
    }

    @Test
    void create_whenAdminEmailInvalid_returnsBadRequest() {
        // @Email 제거 후 service 의 EmailValidator 에서 검증된다.
        var request = new SuperAdminTenantCreateRequest(
                "test-email-invalid",
                "test-email-invalid",
                1,
                "not-an-email",
                "관리자",
                "Test1234!",
                1073741824L, // 1GB
                List.of(), null, null
        );

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isBadRequest());

        assertEquals(BAD_REQUEST.value(), response.getCode());
    }

    @Test
    void create_whenAllowedIpsIsNull_returnsBadRequest() {
        var request = new SuperAdminTenantCreateRequest(
                "test-ips-null",
                "test-ips-null",
                1,
                "admin@thinkfree.com",
                "관리자",
                "Test1234!",
                1073741824L, // 1GB
                null, null, null
        );

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isBadRequest());

        assertEquals(BAD_REQUEST.value(), response.getCode());
    }

    @Test
    //@Transactional
    void update() {
        Integer tenantSeq = 17;
        String url = StringUtil.buildUrl(tenantSeq);
        var request = new SuperAdminTenantUpdateRequest(
                "jwtTest-sunkyoung",
                "test-17",
                1,
                10737418240L, // 10GB
                List.of("192.168.0.12")
        );

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }

    @Test
    //@Transactional
    void update_withAllowedIps() {
        Integer tenantSeq = 1;
        String url = StringUtil.buildUrl(tenantSeq);
        var request = new SuperAdminTenantUpdateRequest(
                "test1",
                "test1",
                1,
                10737418240L, // 10GB
                List.of("192.168.0.12", "192.168.0.0/24")
        );

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isOk());

        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }

    @Test
    void update_whenTenantNameIsBlank_returnsBadRequest() {
        Integer tenantSeq = 1;
        String url = StringUtil.buildUrl(tenantSeq);
        var request = new SuperAdminTenantUpdateRequest(
                "  ",
                "test1",
                1,
                10737418240L, // 10GB
                List.of()
        );

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isBadRequest());

        assertEquals(BAD_REQUEST.value(), response.getCode());
    }
}
