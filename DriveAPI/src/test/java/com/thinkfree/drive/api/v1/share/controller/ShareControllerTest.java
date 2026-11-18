package com.thinkfree.drive.api.v1.share.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.share.request.*;
import com.thinkfree.drive.api.v1.share.response.ShareDetailResponse;
import com.thinkfree.drive.api.v1.share.response.ShareLinkAccessResponse;
import com.thinkfree.drive.api.v1.share.response.ShareTypesResponse;
import com.thinkfree.drive.api.v1.share.response.ShareUserListResponse;
import com.thinkfree.drive.api.v1.share.response.ShareUserSearchResponse;
import com.thinkfree.drive.api.v1.share.target.request.ShareTargetRequestItem;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@Disabled
@WithMockUserCustom
public class ShareControllerTest extends AbstractMvcTest {
    
    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/shares");
    }
    
    @WithMockUserCustom(userSeq = 859, tenantSeq = 1)
    @Test
    @Transactional
    void save() throws InterruptedException {
        //setTestHost("test6.drive.thinkfree.com");
        Long rs = 40103L;
        ShareType st = ShareType.PUBLIC_LINK;
        Boolean cv = true;
        Boolean ce = false;
        Boolean cd = true;
        Boolean cu = false;
        Boolean cs = false;
        
        List<ShareTargetRequestItem> ts = List.of(
                //new ShareTargetRequestItem(876, true, true, true, false, false, true, true),
                new ShareTargetRequestItem(696, true, true, true, false, true, true),
                new ShareTargetRequestItem(21, true, false, true, false, false, true),
                new ShareTargetRequestItem(675, true, false, true, false, false, true)
                //new ShareTargetRequestItem(22, true, true, true, true, true, true, true),
                //new ShareTargetRequestItem(23, true, true, true, true, true, true, true),
                //new ShareTargetRequestItem(26, true, true, true, true, true, true, true)
        );
        
        ShareSaveRequest request = new ShareSaveRequest(rs, st, cv, ce, cd, cu, cs, ts);
        ApiResponse<Void> response = postForObject(request, new TypeReference<>(){}, status().isCreated());
        assertTrue(response.isResult());
        assertEquals(CREATED.value(), response.getCode());
        assertEquals(CREATED.getReasonPhrase(), response.getMessage());
        Thread.sleep(1000);
    }
    
    @WithMockUserCustom(userSeq = 859)
    @Test
    @Transactional
    void update() throws InterruptedException {
        //setTestHost("test6.drive.thinkfree.com");
        Long shareSeq = 1116L;
        ShareType st = ShareType.SPECIFIC_USERS;
        Boolean cv = true;
        Boolean ce = true;
        Boolean cd = true;
        Boolean cu = false;
        Boolean cs = false;
        
        List<ShareTargetRequestItem> ts = List.of(
                //new ShareTargetRequestItem(2, true, true, false, false, false, false, true),
                //new ShareTargetRequestItem(21, true, true, false, false, false, false, true),
                new ShareTargetRequestItem(914, true, true, true, true, true, true)
        );
        
        ShareUpdateRequest request = new ShareUpdateRequest(st, cv, ce, cd, cu, cs, ts);
        ApiResponse<Void> response = patchForObject(shareSeq.toString(), request, new TypeReference<>(){}, status().isOk());
        
        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
        
        Thread.sleep(1000);
    }
    
    
    @WithMockUserCustom(userSeq = 21, tenantSeq = 1)
    @Test
    void detail(){
        
        Long shareSeq = 1116L;
        Integer ss = 859;
        var url = StringUtil.buildUrl(shareSeq);
        var request = new SharePathRequest(null, ss);

        ApiResponse<ShareDetailResponse> response = getForObject(url, request, new TypeReference<>() {}, status().isOk());

        Assertions.assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    
    }
    
    @WithMockUserCustom(userSeq = 859)
    @Test
    @Transactional
    void delete(){
        //setTestHost("test5.drive.thinkfree.com");
        Long shareSeq = 1110L;

        ApiResponse<ShareDetailResponse> response = deleteForObject(shareSeq.toString(), null,  new TypeReference<>() {}, status().isOk());
        
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    
    }

    @WithMockUserCustom(userSeq = 21, tenantSeq = 1)
    @Test
    void searchUsers() {
        //setTestHost("test6.drive.thinkfree.com");
        String searchKeyword = "thinkfree.com";
//        Long shareSeq = 36L;
        Long shareSeq = 1116L;
        Integer ss = 859;
        ShareUserSearchRequest request = new ShareUserSearchRequest(searchKeyword, shareSeq, ss);
        String url = StringUtil.buildUrl("/users/search");
        
        ApiResponse<ShareUserSearchResponse> response = getForObject(url, request, new TypeReference<>() {}, status().isOk());
        Assertions.assertEquals(OK.value(), response.getCode());
        Assertions.assertEquals(OK.getReasonPhrase(), response.getMessage());
    }

    @WithMockUserCustom(userSeq = 21, tenantSeq = 1)
    @Test
    void findShareUsers(){
        
        
        //setTestHost("test6.drive.thinkfree.com");
        Long shareSeq = 1116L;
        Integer ss = 859;
        String url = StringUtil.buildUrl(shareSeq, "users");
        
        Map<String, Object> map = new HashMap<>();
        map.put("ss", ss);

        ApiResponse<List<ShareUserListResponse>> response = getForObject(url, map,  new TypeReference<>() {}, status().isOk());

        Assertions.assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }
    
    @WithMockUserCustom(userSeq = 21, tenantSeq = 1)
    //@WithAnonymousUser
    @Test
    void accessLink_loggedIn(){
        //setTestHost("test6.drive.thinkfree.com");
        String token = "XGBYGrv8OeTJeKM7";
        String url = StringUtil.buildUrl("link", token);

        ApiResponse<ShareLinkAccessResponse> response = getForObject(url, null, new TypeReference<>() {}, status().isOk());

        Assertions.assertNotNull(response.getData());
        Assertions.assertNotNull(response.getData().isPublicLink());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }

    @org.springframework.security.test.context.support.WithAnonymousUser
    @Test
    void accessLink_notLoggedIn(){
        setTestHost("test6.drive.thinkfree.com");
        String token = "Pax9kAckCDYdJxYv";
        String url = StringUtil.buildUrl("link", token);

        ApiResponse<ShareLinkAccessResponse> response = getForObject(url, null, new TypeReference<>() {}, status().isOk());

        Assertions.assertNotNull(response.getData());
        Assertions.assertNotNull(response.getData().isPublicLink());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }
    
    @WithMockUserCustom(userSeq = 902, tenantSeq = 1)
    @Test
    void availableShareTypes(){
        //setTestHost("test6.drive.thinkfree.com");
        Long resourceSeq = 40060L;
        Integer ss = null;
        String url = StringUtil.buildUrl(resourceSeq, "share-types");
        ShareTypesRequest shareTypesRequest = new ShareTypesRequest(resourceSeq, ss);

        ApiResponse<ShareTypesResponse> response = getForObject(url, shareTypesRequest, new TypeReference<>() {}, status().isOk());

        Assertions.assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
        
    }
    
}
