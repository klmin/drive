package com.thinkfree.drive.api.v1.recent.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.recent.request.RecentListRequest;
import com.thinkfree.drive.api.v1.recent.request.RecentSaveRequest;
import com.thinkfree.drive.api.v1.starred.response.StarredListResponse;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.resource.enums.ResourceType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled
@WithMockUserCustom
class RecentControllerTest extends AbstractMvcTest {

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/recent");
    }

    @Test
    @WithMockUserCustom(userSeq = 859)
    void listWithCursor(){

        Integer pi = 1;
        Integer ps = 5;
        ResourceType rt = null;
        String oi = null;
        LocalDate sd = null;
        LocalDate ed = null;
        FileType ft = null;
        Long ci = null;
        Object cv = null;
        ResourceType ct = null;

        var cursorPagingRequest = new CursorPagingRequest(cv, ci, ct, ps);
        var request = new RecentListRequest(rt, oi, sd, ed, ft);

        var params = multiValueMap(request, cursorPagingRequest);

        ApiResponse<List<StarredListResponse>> response = getForObjectWithMultiParams(null, params, new TypeReference<>() {}, status().isOk());

        Assertions.assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
        assertEquals(ps, response.getPageSize());
        Assertions.assertNotNull(response.getHasNext());

    }

    @Test
    @WithMockUserCustom(userSeq = 669)
    void save() {
        
        performRecentSave(25149L, 668);
        performRecentSave(25167L, 668);
        performRecentSave(25162L, 668);
        performRecentSave(25154L, 669);
        performRecentSave(25156L, 669);
        performRecentSave(25159L, 670);
        performRecentSave(25195L, 670);
        /*Long rs = null;
        Integer ss = null;

        RecentSaveRequest request = new RecentSaveRequest(rs, ss);
        ApiResponse<Void> response = postForObject(request, new TypeReference<>(){}, status().isOk());
        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());*/
        

    }
    
    private void performRecentSave(Long rs, Integer ss){
        RecentSaveRequest request = new RecentSaveRequest(rs, ss);
        postForObject(request, new TypeReference<>(){}, status().isOk());
    }
}