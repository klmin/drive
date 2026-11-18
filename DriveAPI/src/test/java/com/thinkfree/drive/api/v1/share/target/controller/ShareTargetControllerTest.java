package com.thinkfree.drive.api.v1.share.target.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.share.target.request.ShareTargetRequestItem;
import com.thinkfree.drive.api.v1.share.target.request.ShareTargetUpsertRequest;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@Disabled
@WithMockUserCustom
class ShareTargetControllerTest extends AbstractMvcTest {
    
    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/shares");
    }
    
    @Test
    @WithMockUserCustom(userSeq=859)
    @Transactional
    void replaceShareTargets() throws InterruptedException {
        
        Long shareSeq = 1110L;
        Integer ss = null;
        
        List<ShareTargetRequestItem> items = new ArrayList<>();
       
        //items.add(new ShareTargetRequestItem(2, true, true, true, true, true, true, true));
        //items.add(new ShareTargetRequestItem(21, true, true, true, true, true, true, true));
        //items.add(new ShareTargetRequestItem(22, true, true, true, true, true, true, true));
        //items.add(new ShareTargetRequestItem(38, true, true, true, true, true, true, true));
        items.add(new ShareTargetRequestItem(902, true, false, false, false, false, true));
        //items.add(new ShareTargetRequestItem(914, true, true, true, true, true, true));
        
        
        ShareTargetUpsertRequest request = new ShareTargetUpsertRequest(ss, items);
        
        String url = StringUtil.buildUrl(shareSeq, "targets");
        
        ApiResponse<Void> response = putForObject(url, request, new TypeReference<>() {}, status().isOk());
        
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
        
        Thread.sleep(2000);
    }
}