package com.thinkfree.drive.resource.query;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.resource.enums.ResourceSearchType;
import com.thinkfree.drive.resource.enums.ResourceSortField;
import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Builder
@Getter
public class ResourceListQuery {
    private Integer userSeq;
    @Setter
    private Integer requestUserSeq;
    private String searchKeyword;
    private Long parentFolderSeq;
    private ResourceType resourceType;
    private Boolean isDelete;
    private ResourceSearchType searchType;
    private String userId;
    private Instant from; // 기간 시작(포함) — 요청 시작일의 00:00(drive.time-zone)
    private Instant to;   // 기간 끝(제외) — 요청 종료일 다음날 00:00
    private FileType fileType;
    private ResourceSortField sortBy;
    private SortOrder sortOrder;
    private CursorPaginationQuery page;
    
}
