package com.thinkfree.drive.api.v1.token.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.token.response.TokenResponse;
import com.thinkfree.drive.constants.DriveConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled
class TokenReissueControllerTest extends AbstractMvcTest {
    
    private static final String baseUrl = "/api/v1/token/reissue";
    
    @Test
    void reissueTokens(){
        
        String testToken = DriveConstants.BEARER + "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyU2VxIjoxLCJpYXQiOjE3NjUyNzEzMDIsImV4cCI6MTc2NTg3NjEwMn0.jYupO4AFHCbeR4Tt_rGz0t7S5I-h4wXBHcb68TDHPsg";
        
        ApiResponse<TokenResponse> response = response(MockMvcRequestBuilders.post(baseUrl).header(HttpHeaders.AUTHORIZATION, testToken), null, null, new TypeReference<>(){}, status().isOk());
        
        Assertions.assertNotNull(response.getData());
        Assertions.assertEquals(HttpStatus.OK.value(), response.getCode());
        Assertions.assertEquals(HttpStatus.OK.getReasonPhrase(), response.getMessage());
        Assertions.assertNotNull(response.getData().getAccessToken());
        Assertions.assertNotNull(response.getData().getRefreshToken());
    }
   
}