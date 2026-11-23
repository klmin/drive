package com.thinkfree.drive.api.v1.user.controller;

import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.systemconfig.response.OfficeResponse;
import com.thinkfree.drive.api.v1.share.response.ShareDetailResponse;
import com.thinkfree.drive.api.v1.user.request.UserSearchRequest;
import com.thinkfree.drive.api.v1.user.response.UserSearchResponse;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import lombok.With;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@WithMockUserCustom
@Transactional
class UserControllerTest extends AbstractMvcTest {
    
    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/users");
    }
    
    @WithMockUserCustom(userSeq=2)
    @Test
    void getCurrentUser() {
        

        ApiResponse<ShareDetailResponse> response = getForObject(null, null, new TypeReference<>() {}, status().isOk());

        Assertions.assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }
    
    @Test
    @WithMockUserCustom(userSeq=859)
    @Disabled("dev DB 고정 데이터 의존(AUTH_009) — DriveFixtures 로 옮길 때 다시 켠다")
    void search(){
        
        String sk = "k";
        UserSearchRequest request = new UserSearchRequest(sk);
        String path = "search";
        
        ApiResponse<List<UserSearchResponse>> response = getForObject(path, request, new TypeReference<>(){}, status().isOk());
        Assertions.assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
        
    }
    
}