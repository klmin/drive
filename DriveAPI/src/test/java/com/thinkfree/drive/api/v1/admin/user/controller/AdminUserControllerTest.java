package com.thinkfree.drive.api.v1.admin.user.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.user.request.AdminUserBulkStatusRequest;
import com.thinkfree.drive.api.v1.admin.user.request.AdminUserBulkUpsertRequest;
import com.thinkfree.drive.api.v1.admin.user.request.AdminUserBulkUpsertRequest.UserItem;
import com.thinkfree.drive.api.v1.admin.user.request.AdminUserListRequest;
import com.thinkfree.drive.api.v1.admin.user.response.AdminUserBulkStatusResponse;
import com.thinkfree.drive.api.v1.admin.user.response.AdminUserBulkUpsertResponse;
import com.thinkfree.drive.api.v1.admin.user.response.AdminUserListResponse;
import com.thinkfree.drive.api.v1.admin.user.response.AdminUserLogResponse;
import com.thinkfree.drive.api.v1.admin.user.response.AdminUserStorageUsageResponse;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@WithMockUserCustom
@Transactional
class AdminUserControllerTest extends AbstractMvcTest {
    
    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/admins/users");
    }
    
    @Test
    void findDisplayableUsersPaginated(){
        
        Object cv = null;
        Long ci = null;
        ResourceType ct = null;
        Integer ps = 5;
        UserStatus s = null;
        String u = "test";

        var request = new AdminUserListRequest(s, u, null);
        var cursorPagingRequest = new CursorPagingRequest(cv, ci, ct, ps);

        MultiValueMap<String, String> params = multiValueMap(request, cursorPagingRequest);
        
        ApiResponse<List<AdminUserListResponse>> response = getForObjectWithMultiParams(null, params, new TypeReference<>() {}, status().isOk());
        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }
    
    @Test
    @Disabled("dev DB 고정 데이터 의존(USER_005) — DriveFixtures 로 옮길 때 다시 켠다")
    void storageUsage() {

        Integer userSeq = 675;
        String url = StringUtil.buildUrl(userSeq, "storage-usage");
        ApiResponse<AdminUserStorageUsageResponse> response = getForObject(url, null, new TypeReference<>(){}, status().isOk());
        Assertions.assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }

    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(USER_005) — DriveFixtures 로 옮길 때 다시 켠다")
    void resetMfa() {
        Integer userSeq = 675;
        String url = StringUtil.buildUrl(userSeq, "mfa", "reset");
        ApiResponse<Void> response = patchForObject(url, null, new TypeReference<>(){}, status().isOk());
        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
    }

    @Test
    void findUserStatusByUserIds() {

        var request = new AdminUserBulkStatusRequest(
                List.of("user1@thinkfree.com", "testuser1@thinkfree.com", "test11", "freefreethink@example.com", "test6000@example.com", "haemmas@naver.com")
        );

        String url = StringUtil.buildUrl("bulk-status");
        ApiResponse<List<AdminUserBulkStatusResponse>> response = postForObject(url, request, new TypeReference<>() {}, status().isOk());
        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
    }

    @Test
    void findUserStatusByUserIds_whenUserIdsEmpty_returnsBadRequest() {

        var request = new AdminUserBulkStatusRequest(Collections.emptyList());

        String url = StringUtil.buildUrl("bulk-status");
        ApiResponse<Void> response = postForObject(url, request, new TypeReference<>() {}, status().isBadRequest());
        assertFalse(response.isResult());
    }

    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(USER_005) — DriveFixtures 로 옮길 때 다시 켠다")
    void deleteUser() {
        Integer userSeq = 675;
        String url = StringUtil.buildUrl(userSeq);
        ApiResponse<Void> response = deleteForObject(url, null, new TypeReference<>(){}, status().isOk());
        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
    }

    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(USER_005) — DriveFixtures 로 옮길 때 다시 켠다")
    void bulkUpsert_insertOnly() {
        var request = new AdminUserBulkUpsertRequest(List.of(
                new UserItem(null, "bulktest11111@test.com", "Test1234!", "벌크유저1", 1000L, List.of(RoleType.USER), "+82", "010-1234-5678"),
                new UserItem(null, "bulktest2111@test.com", "Test1234!", "벌크유저2", 1001L, List.of(RoleType.USER), "82", "01012345679"),
                new UserItem(21, "bulktest311@test.com", "Test1234!", "벌크유저3", 1000727379968L, List.of(RoleType.USER), null, null)
        ));

        String url = StringUtil.buildUrl("bulk");
        ApiResponse<AdminUserBulkUpsertResponse> response = postForObject(url, request, new TypeReference<>(){}, status().isOk());
        log.info("bulkUpsert insertOnly response: {}", response.getData());
        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
    }

    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(USER_005) — DriveFixtures 로 옮길 때 다시 켠다")
    void bulkUpsert_updateOnly() {
        var request = new AdminUserBulkUpsertRequest(List.of(
                new UserItem(772, null, null, "수정유저1", 2L, null, "+82", "010-1234-5675"),
                new UserItem(771, null, null, "수정유저2", 3L, null, "82", "01012345676")
        ));

        String url = StringUtil.buildUrl("bulk");
        ApiResponse<AdminUserBulkUpsertResponse> response = postForObject(url, request, new TypeReference<>(){}, status().isOk());
        log.info("bulkUpsert updateOnly response: {}", response.getData());
        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
    }

    @Test
    //@Transactional
    @Disabled("dev DB 고정 데이터 의존(USER_005) — DriveFixtures 로 옮길 때 다시 켠다")
    void bulkUpsert_mixed() {
        var request = new AdminUserBulkUpsertRequest(List.of(
                new UserItem(null, "bulkmixed@test.com", "Test1234!", "신규유저", 1073741824L, List.of(RoleType.USER), null, null),
                new UserItem(null, "bulkmixed2@test.com", "Test1234!", "신규유저2", 1073741824L, List.of(RoleType.USER), "82", "01000190029"),
                new UserItem(null, "bulkmixed3@test.com", "Test1234!", "신규유저3", 1073741824L, List.of(RoleType.USER), "82", "01000190030"),
                new UserItem(772, null, null, "수정유저10", 1073741824L, null, "+82", "010-1234-5675"),
                new UserItem(771, null, null, "수정유저20", 1073741824L, null, "82", "01012345676")
        ));

        String url = StringUtil.buildUrl("bulk");
        ApiResponse<AdminUserBulkUpsertResponse> response = postForObject(url, request, new TypeReference<>(){}, status().isOk());
        log.info("bulkUpsert mixed response: {}", response.getData());
        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
    }

    @Test
    void bulkUpsert_emptyUsers_returnsBadRequest() {
        var request = new AdminUserBulkUpsertRequest(Collections.emptyList());

        String url = StringUtil.buildUrl("bulk");
        ApiResponse<Void> response = postForObject(url, request, new TypeReference<>(){}, status().isBadRequest());
        assertFalse(response.isResult());
    }

    @Test
    @Disabled("dev DB 고정 데이터 의존(USER_005) — DriveFixtures 로 옮길 때 다시 켠다")
    void getUserLogs() {

        Integer userSeq = 7;
        Object cv = null;
        Long ci = null;
        ResourceType ct = null;
        Integer ps = 10;

        var cursorPagingRequest = new CursorPagingRequest(cv, ci, ct, ps);
        MultiValueMap<String, String> params = multiValueMap(cursorPagingRequest);

        String url = StringUtil.buildUrl(userSeq, "log");
        ApiResponse<List<AdminUserLogResponse>> response = getForObjectWithMultiParams(url, params, new TypeReference<>() {}, status().isOk());

        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }

    @Test
    void bulkUpsert_insertMissingRequiredField_returnsBadRequest() {
        var request = new AdminUserBulkUpsertRequest(List.of(
                new UserItem(null, "teset222@test.com", "Test1234!", null, 1073741824L, List.of(RoleType.USER), "aa", null),
                new UserItem(null, "teset232@test.com", "Test1234!", "유저", 1073741824L, List.of(RoleType.USER), "aa", null)
        ));

        String url = StringUtil.buildUrl("bulk");
        ApiResponse<Void> response = postForObject(url, request, new TypeReference<>(){}, status().isBadRequest());
        assertFalse(response.isResult());
    }

}