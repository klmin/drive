package com.thinkfree.drive.api.v1.trash.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.starred.response.StarredListResponse;
import com.thinkfree.drive.api.v1.trash.request.TrashFileRestoreRequest;
import com.thinkfree.drive.api.v1.trash.request.TrashFolderRestoreRequest;
import com.thinkfree.drive.api.v1.trash.request.TrashListRequest;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.trash.enums.TrashSortField;
import com.thinkfree.drive.util.StringUtil;
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
class TrashControllerTest extends AbstractMvcTest {
    
    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/trash");
    }
    
    @WithMockUserCustom(userSeq = 859)
    @Test
    void listWithCursor() {

        Integer ps = 5;
        ResourceType rt = null;
        String di = null;
        LocalDate sd = null;
        LocalDate ed = null;
        FileType ft = null;
        TrashSortField sb = TrashSortField.REGISTER;
        SortOrder so = SortOrder.DESC;
        Long ci = null;
        Object cv = null;

        var cursorPagingRequest = new CursorPagingRequest(cv, ci, null, ps);
        var trashListRequest = new TrashListRequest(rt, di, sd, ed, ft, sb, so);

        var params = multiValueMap(trashListRequest, cursorPagingRequest);

        ApiResponse<List<StarredListResponse>> response = getForObjectWithMultiParams(null, params, new TypeReference<>() {}, status().isOk());

        Assertions.assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
        assertEquals(ps, response.getPageSize());
        Assertions.assertNotNull(response.getHasNext());

    }

    @Test
    @WithMockUserCustom(userSeq = 668)
    void restoreFile(){
        Long resourceSeq = 25162L;
        String url = StringUtil.buildUrl("files", resourceSeq, "restore");
        TrashFileRestoreRequest request = new TrashFileRestoreRequest(false);

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>(){}, status().isOk());
        assertEquals(OK.value(), response.getCode());
    }

    @Test
    @WithMockUserCustom(userSeq = 1)
    void restoreFolder(){
        Long resourceSeq = 22366L;
        String url = StringUtil.buildUrl("folders", resourceSeq, "restore");
        TrashFolderRestoreRequest request = new TrashFolderRestoreRequest(false);

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>(){}, status().isOk());
        assertEquals(OK.value(), response.getCode());
    }

    @Test
    @WithMockUserCustom(userSeq = 859)
    //@Transactional
    void deleteFile() throws InterruptedException {
        Long resourceSeq = 40052L;
        String url = StringUtil.buildUrl("files", resourceSeq);

        ApiResponse<Void> response = deleteForObject(url, null, new TypeReference<>(){}, status().isOk());
        assertEquals(OK.value(), response.getCode());
        
        Thread.sleep(5000);
        
    }
    
    @Test
    //@Transactional
    @WithMockUserCustom(userSeq = 1)
    void deleteFolder() throws InterruptedException {
        Long resourceSeq = 23776L;
        String url = StringUtil.buildUrl("folders", resourceSeq);

        ApiResponse<Void> response = deleteForObject(url, null, new TypeReference<>(){}, status().isOk());
        assertEquals(OK.value(), response.getCode());
        
        Thread.sleep(5000);
    }
}