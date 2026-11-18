package com.thinkfree.drive.api.v1.starred.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.page.request.PagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.starred.request.StarredListRequest;
import com.thinkfree.drive.api.v1.starred.request.StarredSaveRequest;
import com.thinkfree.drive.api.v1.starred.response.StarredListResponse;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortField;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.resource.enums.ResourceType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled
@WithMockUserCustom
class StarredControllerTest extends AbstractMvcTest {

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/starred");
    }

    @Test
    @WithMockUserCustom(userSeq = 859)
    void list(){
        
        Integer pi = 1;
        Integer ps = 10;
        ResourceType rt = null;
        String oi = null;
        LocalDate sd = null;
        LocalDate ed = null;
        FileType ft = null;
        SortField sb = SortField.NAME;
        SortOrder so = SortOrder.DESC;
        
        var pagingRequest = new PagingRequest(pi, ps);
        var request = new StarredListRequest(rt, oi, sd, ed, ft, sb, so);
        var params = multiValueMap(request, pagingRequest);

        ApiResponse<List<StarredListResponse>> response = getForObjectWithMultiParams(null, params, new TypeReference<>() {}, status().isOk());

        Assertions.assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
        assertEquals(ps, response.getPageSize());
    }

    @Test
    @WithMockUserCustom(userSeq = 669)
    void listWithCursor(){

        Integer ps = 20;
        ResourceType rt = null;
        String oi = null;
        LocalDate sd = null;
        LocalDate ed = null;
        FileType ft = null;
        SortField sb = null;
        SortOrder so = null;
        Long ci = null;
        Object cv = null;
        ResourceType ct = null;


        var cursorPagingRequest = new CursorPagingRequest(cv, ci, ct, ps);
        var request = new StarredListRequest(rt, oi, sd, ed, ft, sb, so);
        var params = multiValueMap(request, cursorPagingRequest);

        ApiResponse<List<StarredListResponse>> response = getForObjectWithMultiParams(null, params, new TypeReference<>() {}, status().isOk());

        Assertions.assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
        assertEquals(ps, response.getPageSize());
        Assertions.assertNotNull(response.getHasNext());
    }

    @Test
    @WithMockUserCustom(userSeq = 21)
    void save() {
        
        performStarredSave(25251L, 21);
        /*performStarredSave(25167L, 668);
        performStarredSave(25162L, 668);
        performStarredSave(25154L, 669);
        performStarredSave(25156L, 669);
        performStarredSave(25159L, 670);
        performStarredSave(25195L, 670);*/
        
//        Long rs = 602L;
//        Integer ss = 21;
//
//        StarredSaveRequest request = new StarredSaveRequest(rs, ss);
//        ApiResponse<Void> response = postForObject(request, new TypeReference<>(){}, status().isCreated());
//        assertTrue(response.isResult());
//        assertEquals(CREATED.value(), response.getCode());
//        assertEquals(CREATED.getReasonPhrase(), response.getMessage());
        
    }

    @Test
    @WithMockUserCustom(userSeq = 1)
    void delete(){
        Long starredSeq = 92L;
        ApiResponse<Void> response = deleteForObject(String.valueOf(starredSeq), null, new TypeReference<>(){}, status().isOk());
        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }
    
    private void performStarredSave(Long rs, Integer ss){
        StarredSaveRequest request = new StarredSaveRequest(rs, ss);
        postForObject(request, new TypeReference<>(){}, status().isCreated());
    }
}