package com.thinkfree.drive.share.target.service;

import com.thinkfree.drive.common.DriveIntegrationTest;
import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.target.enums.ShareTargetSortField;
import com.thinkfree.drive.share.target.query.ShareTargetListQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

@DriveIntegrationTest
class ShareTargetFindServiceTest {
    
    @Autowired
    public ShareTargetFindService service;
    
    @Test
    void findAll() {
        
        Integer userSeq = 1;
        Boolean isDelete = false;
        ResourceType resourceType = null;
        FileType fileType = null;
        String userId = null;
        LocalDate startDate = null;
        LocalDate endDate = null;
        ShareTargetSortField sortBy = null;
        SortOrder sortOrder = null;
        Integer ps = 10;
        Long ci = null;
        Object cv = null; // sb 기준에 맞춰서 리소스 값 저장 ex) UPDATE -> LocalDate.of(2025, 6, 12)
        ResourceType ct = null;
        CursorPaginationQuery page = CursorPaginationQuery.of(cv, ci, ct, ps);
        
        var query = ShareTargetListQuery.builder()
                                        .userSeq(userSeq)
                                        .isDelete(isDelete)
                                        .resourceType(resourceType)
                                        .fileType(fileType)
                                        .userId(userId)
                                        .from(DriveTime.startOfDay(startDate))
                                        .to(DriveTime.endExclusive(endDate))
                                        .sortBy(sortBy)
                                        .sortOrder(sortOrder)
                                        //.page(page)
                                        .build();
        
        
        service.findAll(query);
    }
    
    @Test
    void countAll() {
    }
}