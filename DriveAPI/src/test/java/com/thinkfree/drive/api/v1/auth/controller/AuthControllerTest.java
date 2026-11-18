package com.thinkfree.drive.api.v1.auth.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.auth.request.AnonymousLoginRequest;
import com.thinkfree.drive.api.v1.auth.request.LoginRequest;
import com.thinkfree.drive.api.v1.auth.response.AnonymousLoginResponse;
import com.thinkfree.drive.api.v1.auth.response.LoginConfigResponse;
import com.thinkfree.drive.api.v1.auth.response.LoginResponse;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.util.StringUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled
class AuthControllerTest extends AbstractMvcTest {
    
    private static final String baseUrl = "/api/v1/auth";
    
    @Test
    void login() {
        
        String userId = "user1@thinkfree.com";
        String encPwd = "User1234!";
        String url = StringUtil.buildUrl(baseUrl, "login");
        var request = new LoginRequest(userId, encPwd);
        
        ApiResponse<LoginResponse> response = response(withHost(MockMvcRequestBuilders.post(url)), request, null, new TypeReference<>(){}, status().isOk());

        Assertions.assertNotNull(response.getData());
        Assertions.assertEquals(HttpStatus.OK.value(), response.getCode());
        Assertions.assertEquals(HttpStatus.OK.getReasonPhrase(), response.getMessage());
        Assertions.assertNotNull(response.getData().getAccessToken());
        Assertions.assertNotNull(response.getData().getRefreshToken());
        Assertions.assertNotNull(response.getData().getUserId());
        Assertions.assertNotNull(response.getData().getUserName());
        Assertions.assertNotNull(response.getData().getHasChangedPassword());
        Assertions.assertNotNull(response.getData().getBaseDomain());
    }

    @Test
    void adminLogin() {

        String userId = "admin@thinkfree.com";
        String encPwd = "Admin1234!";
        String url = StringUtil.buildUrl(baseUrl, "admin/login");
        var request = new LoginRequest(userId, encPwd);

        ApiResponse<LoginResponse> response = response(withHost(MockMvcRequestBuilders.post(url)), request, null, new TypeReference<>(){}, status().isOk());

        Assertions.assertNotNull(response.getData());
        Assertions.assertEquals(HttpStatus.OK.value(), response.getCode());
        Assertions.assertEquals(HttpStatus.OK.getReasonPhrase(), response.getMessage());
        Assertions.assertNotNull(response.getData().getAccessToken());
        Assertions.assertNotNull(response.getData().getRefreshToken());
        Assertions.assertNotNull(response.getData().getUserId());
        Assertions.assertNotNull(response.getData().getUserName());
        Assertions.assertNotNull(response.getData().getHasChangedPassword());
        Assertions.assertNotNull(response.getData().getBaseDomain());
        Assertions.assertNotNull(response.getData().getIsFirstLogin());
    }

    @Test
    void anonymousLogin() {
        String url = StringUtil.buildUrl(baseUrl, "anonymous/login");
        var request = new AnonymousLoginRequest("Ndme8K5PkjsNCuNB"); // 전체 공유한 리소스 공유링크토큰

        ApiResponse<AnonymousLoginResponse> response = response(withHost(MockMvcRequestBuilders.post(url)), request, null, new TypeReference<>() {}, status().isOk());

        Assertions.assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
        Assertions.assertNotNull(response.getData().getAccessToken());
        Assertions.assertNotNull(response.getData().getRefreshToken());
        Assertions.assertNotNull(response.getData().getUserSeq());
        Assertions.assertNotNull(response.getData().getUserId());
        Assertions.assertNotNull(response.getData().getBaseDomain());
    }
    
    @Test
    void logout() {

        String testToken = DriveConstants.BEARER + "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyX3NlcSI6NDEsInR5cGUiOiJhY2Nlc3MiLCJzaWQiOiIwMTllM2RkNi0xNWM2LTc1ODYtODYxMi04YWY1ZjY0OGZiYzkiLCJpYXQiOjE3NzkxNjc4NTQsImV4cCI6MTc3OTE3MTQ1NH0.2CX87_4Mp502Lp_JjGzEgm3wYbovLWZN_NRAZagpef4";
        String url = StringUtil.buildUrl(baseUrl, "logout");

        ApiResponse<Void> response = response(withHost(MockMvcRequestBuilders.get(url)).header(HttpHeaders.AUTHORIZATION, testToken), null, null, new TypeReference<>(){}, status().isOk());
        Assertions.assertEquals(HttpStatus.OK.value(), response.getCode());

    }
    
    @Test
    void loginConfig(){
        
        String url = StringUtil.buildUrl(baseUrl, "login-config");

        ApiResponse<LoginConfigResponse> response = getForObject(url, null, new TypeReference<>() {}, status().isOk());
        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }


}