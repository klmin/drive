package com.thinkfree.drive.api.v1.admin.user.pendingdelete.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.user.pendingdelete.request.AdminPendingDeletePermanentRequest;
import com.thinkfree.drive.api.v1.admin.user.pendingdelete.request.AdminPendingDeleteUserListRequest;
import com.thinkfree.drive.api.v1.admin.user.pendingdelete.response.AdminPendingDeleteUserListResponse;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.util.StringUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockUserCustom
@Transactional
class AdminPendingDeleteUserControllerTest extends AbstractMvcTest {
    
    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/admins/users/pending-delete");
    }
    
    @WithMockUserCustom
    @Test
    void findAllCursorPaginated() {
        
        //Object cv = null;
        //Long ci = null;
        LocalDate sd = LocalDate.of(2025,10,5);
        LocalDate ed = LocalDate.of(2025,10,8);
        Object cv = null;
        Long ci = null;
        ResourceType ct = null;
        Integer ps = 5;
        var request = new AdminPendingDeleteUserListRequest(sd, ed, null, null);

        var cursorPagingRequest = new CursorPagingRequest(cv, ci, ct, ps);

        MultiValueMap<String, String> params = multiValueMap(request, cursorPagingRequest);

        ApiResponse<List<AdminPendingDeleteUserListResponse>> response = getForObjectWithMultiParams(null, params, new TypeReference<>() {}, status().isOk());

        Assertions.assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
        assertEquals(ps, response.getPageSize());
        Assertions.assertNotNull(response.getHasNext());
    }
    
    @WithMockUserCustom
    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(USER_005) — DriveFixtures 로 옮길 때 다시 켠다")
    void restore(){
        
        Integer userSeq = 675;
        String url = StringUtil.buildUrl(userSeq, "restore");
        
        ApiResponse<Void> response = patchForObject(url, null, new TypeReference<>() {}, status().isOk());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }
    
    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(USER_005) — DriveFixtures 로 옮길 때 다시 켠다")
    void permanentDelete() throws InterruptedException {
        List<Integer> ul = List.of(914, 675);
        var request = new AdminPendingDeletePermanentRequest(ul);
        String url = StringUtil.buildUrl("permanent-delete");
        
        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isOk());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
        
        Thread.sleep(3000);
    }
}