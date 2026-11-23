package com.thinkfree.drive.api.v1.admin.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.request.AdminUserUpdateRequest;
import com.thinkfree.drive.api.v1.admin.request.UserRegisterRequest;
import com.thinkfree.drive.api.v1.admin.response.AdminResponse;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.global.enums.AuthSource;
import com.thinkfree.drive.util.StringUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockUserCustom(authSource = AuthSource.EXTERNAL)
@Transactional
class ExternalAdminControllerTest extends AbstractMvcTest {
    
    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/public/v1/admins/users");
    }

    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(상태 코드 불일치) — DriveFixtures 로 옮길 때 다시 켠다")
    void registerUser() {

        String userId = "test600112@example.com";
        String encPwd = "test@1234!A";
        String userName = "테스트계정";
        Long totalQuotaByte = 1000000000L;
        String roleType = "USER";
        MockMultipartFile profileImageFile = null;
        String ct = "+82";
        String phoneNumber = "010-1234-1111";

        UserRegisterRequest request = new UserRegisterRequest(
                userId,
                encPwd,
                userName,
                totalQuotaByte,
                List.of(RoleType.valueOf(roleType)),
                null,
                ct,
                phoneNumber
        );
        
        ApiResponse<Void> response = multipartForObject(profileImageFile, request, new TypeReference<>() {}, status().isCreated());
        assertTrue(response.isResult());
        assertEquals(CREATED.value(), response.getCode());
        assertEquals(CREATED.getReasonPhrase(), response.getMessage());
    }

    @Test
    @Disabled("dev DB 고정 데이터 의존(USER_005) — DriveFixtures 로 옮길 때 다시 켠다")
    void getUser() {

        int userSeq = 715;
        String url = StringUtil.buildUrl(userSeq);

        ApiResponse<AdminResponse> response = getForObject(url, null, new TypeReference<>() {}, status().isOk());
        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }

    @Test
    @Transactional
    @Disabled("dev DB 고정 데이터 의존(USER_005) — DriveFixtures 로 옮길 때 다시 켠다")
    void updateUserByAdmin() {

        Integer userSeq = 715;
        String un = "홍길동";
        Long tq = 5040236234L;
        String ct = "82";
        String pn = "1012345678";
        String url = StringUtil.buildUrl(userSeq);
        MockMultipartFile pif = null;
        boolean dpi = false;

        AdminUserUpdateRequest request = new AdminUserUpdateRequest(
                un,
                tq,
                ct,
                pn,
                null,
                dpi,
                null
        );
        
        
        ApiResponse<Void> response = multipartForObject(url, pif, HttpMethod.PATCH, request, new TypeReference<>(){}, status().isOk());
        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }

    @Test
    @Disabled("dev DB 고정 데이터 의존(USER_005) — DriveFixtures 로 옮길 때 다시 켠다")
    void activateUser() {
        Integer userSeq = 723;
        String url = StringUtil.buildUrl(userSeq, "activate");

        ApiResponse<Void> response = patchForObject(url, null, null, new TypeReference<>() {}, status().isOk());
        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }

    @Test
    @Disabled("dev DB 고정 데이터 의존(USER_005) — DriveFixtures 로 옮길 때 다시 켠다")
    void deactivateUser() {
        Integer userSeq = 723;
        String url = StringUtil.buildUrl(userSeq, "deactivate");

        ApiResponse<Void> response = patchForObject(url, null, null, new TypeReference<>() {}, status().isOk());
        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }
}
