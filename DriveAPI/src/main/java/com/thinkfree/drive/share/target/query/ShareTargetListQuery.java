package com.thinkfree.drive.share.target.query;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.target.enums.ShareTargetSortField;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Builder
@Getter
public class ShareTargetListQuery {
    
    private Integer userSeq;
    private Boolean isDelete;
    private ResourceType resourceType;
    private FileType fileType;
    private String userId;
    private Instant from; // 기간 시작(포함) — 요청 시작일의 00:00(drive.time-zone)
    private Instant to;   // 기간 끝(제외) — 요청 종료일 다음날 00:00
    private ShareTargetSortField sortBy;
    private SortOrder sortOrder;
    private CursorPaginationQuery page;
    
}
