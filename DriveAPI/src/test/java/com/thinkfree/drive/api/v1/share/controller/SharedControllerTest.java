package com.thinkfree.drive.api.v1.share.controller;

import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.page.request.PagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.resource.response.ResourceListResponse;
import com.thinkfree.drive.api.v1.share.request.SharedListRequest;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.target.enums.ShareTargetSortField;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@WithMockUserCustom
@Transactional
class SharedControllerTest extends AbstractMvcTest {
    
    
    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/shared");
    }
    
    @WithMockUserCustom(userSeq = 859)
    @Test
    @Disabled("dev DB 고정 데이터 의존(AUTH_009) — DriveFixtures 로 옮길 때 다시 켠다")
    void list() {
        
        Integer pageIndex = 1;
        Integer pageSize = 10;
        /*ResourceType resourceType = ResourceType.FILE;
        FileType fileType = FileType.DOCUMENT;
        String userId = "testuser1@thinkfree.com";
        LocalDate startDate = null;
        LocalDate endDate = null;
        ShareTargetSortField sortBy = ShareTargetSortField.UPDATE;
        SortOrder sortOrder = SortOrder.DESC;*/
        String oi = null;
        ResourceType resourceType = null;
        FileType fileType = null;
        String userId = null;
        LocalDate startDate = null;
        LocalDate endDate = null;
        ShareTargetSortField sortBy = null;
        SortOrder sortOrder = null;
        
        var request = new SharedListRequest(resourceType, fileType, oi, startDate, endDate, sortBy, sortOrder);
        
        var pagingRequest = new PagingRequest(pageIndex, pageSize);
        
        MultiValueMap<String, String> params = multiValueMap(request, pagingRequest);
        
        ApiResponse<List<ResourceListResponse>> response = getForObjectWithMultiParams(null, params, new TypeReference<>() {}, status().isOk());
        
        Assertions.assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
        assertEquals(pageSize, response.getPageSize());
        
    }
    
}