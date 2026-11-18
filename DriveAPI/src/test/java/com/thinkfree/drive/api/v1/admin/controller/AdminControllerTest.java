package com.thinkfree.drive.api.v1.admin.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.request.AdminImportCsvRequest;
import com.thinkfree.drive.api.v1.admin.request.AdminUserUpdateRequest;
import com.thinkfree.drive.api.v1.admin.request.UserRegisterRequest;
import com.thinkfree.drive.api.v1.admin.response.AdminImportCsvResponse;
import com.thinkfree.drive.api.v1.admin.response.AdminResponse;
import com.thinkfree.drive.api.v1.user.request.PasswordUpdateRequest;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.util.StringUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled
@WithMockUserCustom(tenantSeq = 1)
class AdminControllerTest extends AbstractMvcTest {
    
    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/admins");
        setTestSlug("default");
    }
    
    
    
    @Test
    //@Transactional
    void registerUser(){
        
        String userId = "user";
        String encPwd = "User1234!";
        String userName = "유저";
        Long totalQuotaByte = 1073741824L;
        String roleType = "USER";
        MockMultipartFile profileImageFile = createMultipartFileFromClasspath("pif", "branding/default/favicon.ico", "image/vnd.microsoft.icon");
        //MockMultipartFile profileImageFile = null;
        String ct = null;
        String phoneNumber = null;

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
        
        ApiResponse<Void> response = multipartForObject("users", null, request, new TypeReference<>() {}, status().isCreated());
        assertTrue(response.isResult());
        assertEquals(CREATED.value(), response.getCode());
        assertEquals(CREATED.getReasonPhrase(), response.getMessage());
    }
    
    @Test
    void getUser(){
        
        int userSeq = 1;
        String url = StringUtil.buildUrl("users", userSeq);
        
        ApiResponse<AdminResponse> response = getForObject(url, null, new TypeReference<>() {}, status().isOk());
        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }
    
    @Test
    @Transactional
    void updateUserByAdmin(){
        
        Integer userSeq = 675;
        String un = "테스트유저1";
        Long tq = 1000727379968L;
        String ct = null;
        String pn = null;
        String url = StringUtil.buildUrl("users", userSeq);
        MockMultipartFile pif = createMultipartFileFromClasspath("pif", "branding/default/favicon.ico", "image/vnd.microsoft.icon");
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
    
    @WithMockUserCustom//(tenantSeq = 3)
    @Test
    @Transactional
    void updatePassword(){
        
        
        //setTestHost("default-add.drive.thinkfree.com");
        Integer userSeq = 21;
        String url = StringUtil.buildUrl("users", userSeq, "password");
        String currentPwd = null;
        String newPwd = "Test1234!";
        var request = new PasswordUpdateRequest(currentPwd, newPwd);
        
        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>(){}, status().isOk());
        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }
    
    
    @Test
    //@Transactional
    void activateUser(){
        Integer userSeq = 21;
        String url = StringUtil.buildUrl("users", userSeq, "activate");
        
        ApiResponse<Void> response = patchForObject(url, null, null, new TypeReference<>(){}, status().isOk());
        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }
    
    @Test
    @WithMockUserCustom(tenantSeq = 3)
    void deactivateUser(){
        setTestHost("default-add.drive.thinkfree.com");
        Integer userSeq = 675;
        String url = StringUtil.buildUrl("users", userSeq, "deactivate");
        
        ApiResponse<Void> response = patchForObject(url, null, null, new TypeReference<>(){}, status().isOk());
        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }
    
    @Test
    @Transactional
    void withdraw(){
        
        Integer userSeq = 675;
        String url = StringUtil.buildUrl("users", userSeq, "withdraw");
        
        ApiResponse<Void> response = patchForObject(url, null, null, new TypeReference<>(){}, status().isOk());
        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }
    
    @Test
    void importCsvSample() throws Exception {
        
        String url = "/users/import/csv/sample";
        
        var response = mvcResultResponse(get(url), null, null, true, status().isOk());
        
        String contentDisposition = response.getHeader(HttpHeaders.CONTENT_DISPOSITION);
        String contentType = response.getHeader(HttpHeaders.CONTENT_TYPE);
        
        assertNotNull(contentDisposition);
        assertNotNull(contentType);
        
        String extractedFileName = extractFileNameFromContentDisposition(contentDisposition);
        
        File downloadFile = new File(extractedFileName);
        try (FileOutputStream fileOutputStream = new FileOutputStream(downloadFile)) {
            fileOutputStream.write(response.getContentAsByteArray());
        }
        
        assertTrue(downloadFile.exists());
        assertEquals(String.valueOf(downloadFile.length()), response.getHeader(HttpHeaders.CONTENT_LENGTH));
        assertArrayEquals(Files.readAllBytes(downloadFile.toPath()), response.getContentAsByteArray());
        assertTrue(downloadFile.delete());
    }
    
    @Test
    @Transactional
    void importCsv() throws Exception {
        
        String url = "/users/import/csv";
        
        var multipartFile = createMockMultipartFile("admin/template/admin-users-import_sample.csv", "text/csv");
        //var multipartFile = createMockMultipartFile("files/import-csv-utf-8.csv", "text/csv");
        //var multipartFile = createMockMultipartFile("files/import-csv-utf-8-fail-test.csv", "text/csv");
        var request = multiValueMap(new AdminImportCsvRequest(null, true));
        
        var response = mvcResultResponse(multipart(url).file(multipartFile).contentType(MediaType.MULTIPART_FORM_DATA), null, request, false);
        String contentDisposition = response.getHeader(HttpHeaders.CONTENT_DISPOSITION);
        
        if(contentDisposition == null){
            ApiResponse<AdminImportCsvResponse.Success> apiResponse = readValue(response.getContentAsString(), new TypeReference<>() {});
            assertNotNull(apiResponse.getData());
            assertTrue(apiResponse.getData().insertCount() > 0);
            assertEquals(CREATED.value(), apiResponse.getCode());
            
        }else{
            
            String extractedFileName = extractFileNameFromContentDisposition(contentDisposition);

            File downloadFile = new File(extractedFileName);
            try (FileOutputStream fileOutputStream = new FileOutputStream(downloadFile)) {
                fileOutputStream.write(response.getContentAsByteArray());
            }
    
            assertTrue(downloadFile.exists());
            assertEquals(String.valueOf(downloadFile.length()), response.getHeader(HttpHeaders.CONTENT_LENGTH));
            assertArrayEquals(Files.readAllBytes(downloadFile.toPath()), response.getContentAsByteArray());
            //assertTrue(downloadFile.delete());
        }
        
        Thread.sleep(2000);
        
    }
    
}