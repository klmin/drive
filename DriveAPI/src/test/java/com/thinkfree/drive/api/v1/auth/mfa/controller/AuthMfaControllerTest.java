package com.thinkfree.drive.api.v1.auth.mfa.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.auth.mfa.request.MfaConfirmRequest;
import com.thinkfree.drive.api.v1.auth.mfa.request.MfaSetupRequest;
import com.thinkfree.drive.api.v1.auth.mfa.request.MfaVerifyRequest;
import com.thinkfree.drive.user.enums.MfaMethod;
import com.thinkfree.drive.util.StringUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled
class AuthMfaControllerTest extends AbstractMvcTest {
    
    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/auth/mfa");
    }
    
    @Test
    void setup(){
        
        String t = "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyX3NlcSI6Njk2LCJ0eXBlIjoibWZhX3NldHVwX2NoYWxsZW5nZSIsImlhdCI6MTc3NDM0MDA1OCwiZXhwIjoxNzc0MzQwMzU4fQ.hYE3F1wMBry_tTQrh1Je_40k7TlWN_iNoUbhcT5TMZg";
        MfaMethod m = MfaMethod.TOTP;
        String url = StringUtil.buildUrl("setup");
        MfaSetupRequest request = new MfaSetupRequest(t, m);

        ApiResponse<Void> response = postForObject(url, request, new TypeReference<>(){}, status().isOk());

        Assertions.assertTrue(response.isResult());
        Assertions.assertEquals(HttpStatus.OK.value(), response.getCode());
        Assertions.assertEquals(HttpStatus.OK.getReasonPhrase(), response.getMessage());
        
    }
    
    @Test
    void confirm(){
        String t = "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyX3NlcSI6Njk2LCJ0eXBlIjoibWZhX2NvbmZpcm1fY2hhbGxlbmdlIiwiaWF0IjoxNzc0MzQwMTQ5LCJleHAiOjE3NzQzNDAzMjl9.fdYBUr2IerkzA6AepHMzTJifKA23ttpPeK9rPhBtcro";
        String c = "932422";
        MfaMethod m = MfaMethod.TOTP;
        String url = StringUtil.buildUrl("confirm");
        MfaConfirmRequest request = new MfaConfirmRequest(t, c, m);

        ApiResponse<Void> response = postForObject(url, request, new TypeReference<>(){}, status().isOk());

        Assertions.assertTrue(response.isResult());
        Assertions.assertEquals(HttpStatus.OK.value(), response.getCode());
        Assertions.assertEquals(HttpStatus.OK.getReasonPhrase(), response.getMessage());
    }
    
    @Test
    void verifyMfa() {
        String t = "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyX3NlcSI6Njk2LCJ0eXBlIjoibWZhX2NoYWxsZW5nZSIsImlhdCI6MTc3NDM0MDM2NCwiZXhwIjoxNzc0MzQwNDg0fQ.Pb9toayllOLkWvYnBHCpzLGsDX5RBgpDJVa13-WrF7Y";
        String c = "271819";
        String url = StringUtil.buildUrl("verify");
        MfaVerifyRequest request = new MfaVerifyRequest(t, c);

        ApiResponse<Void> response = postForObject(url, request, new TypeReference<>(){}, status().isOk());

        Assertions.assertTrue(response.isResult());
        Assertions.assertEquals(HttpStatus.OK.value(), response.getCode());
        Assertions.assertEquals(HttpStatus.OK.getReasonPhrase(), response.getMessage());
    }
    
}