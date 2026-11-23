package com.thinkfree.drive.api.v1.pinnedfolder.controller;

import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.pinnedfolder.request.PinnedFolderSaveRequest;
import com.thinkfree.drive.api.v1.starred.response.StarredListResponse;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@WithMockUserCustom(userSeq=2)
@Transactional
class PinnedFolderControllerTest extends AbstractMvcTest {
   
    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/pinned-folders");
    }
    
    @WithMockUserCustom(userSeq = 21)
    @Test
    @Disabled("dev DB 고정 데이터 의존(AUTH_009) — DriveFixtures 로 옮길 때 다시 켠다")
    void listPinnedFolders(){

        ApiResponse<List<StarredListResponse>> response = getForObject(null, null, new TypeReference<>() {}, status().isOk());

        Assertions.assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
        
    }
    
    @WithMockUserCustom(userSeq=44)
    @Test
    @Disabled("dev DB 고정 데이터 의존(AUTH_009) — DriveFixtures 로 옮길 때 다시 켠다")
    void pinFolder() {
        
        Long rs = 17466L;
        Integer ss = 43;
        
        var request = new PinnedFolderSaveRequest(rs, ss);

        ApiResponse<Void> response = postForObject(request, new TypeReference<>() {}, status().isCreated());
        assertEquals(CREATED.value(), response.getCode());
        assertEquals(CREATED.getReasonPhrase(), response.getMessage());
        
    }
    
    @WithMockUserCustom(userSeq = 21)
    @Test
    @Disabled("dev DB 고정 데이터 의존(AUTH_009) — DriveFixtures 로 옮길 때 다시 켠다")
    void unpinFolder(){
        
        Long pinnedFolderSeq = 5L;
        String url = StringUtil.buildUrl(pinnedFolderSeq);
        
        ApiResponse<Void> response = deleteForObject(url, null, new TypeReference<>() {}, status().isOk());
        
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }
}