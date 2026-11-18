package com.thinkfree.drive.trash.query;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.trash.enums.TrashSortField;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class TrashListQuery {
    
    private Integer userSeq;
    private ResourceType resourceType;
    private String deleteUserId;
    private Instant from; // 기간 시작(포함) — 요청 시작일의 00:00(drive.time-zone)
    private Instant to;   // 기간 끝(제외) — 요청 종료일 다음날 00:00
    private FileType fileType;
    private TrashSortField sortBy;
    private SortOrder sortOrder;
    private CursorPaginationQuery page;
    
}
