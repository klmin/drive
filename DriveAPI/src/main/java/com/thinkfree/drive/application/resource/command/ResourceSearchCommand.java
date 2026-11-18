package com.thinkfree.drive.application.resource.command;

import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.resource.enums.ResourceSearchType;
import com.thinkfree.drive.resource.enums.ResourceSortField;
import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.Builder;

import java.time.LocalDate;

/**
 * 인자가 많아 위치 기반 생성이 위험하므로 직접 생성하는 어댑터(MCP 툴)는 builder로 조립한다.
 * (웹 어댑터는 MapStruct RequestConverter가 이름 기반으로 생성)
 */
@Builder
public record ResourceSearchCommand
(
    Integer sharedByUserSeq,
    String searchKeyword,
    Long parentFolderSeq,
    ResourceType resourceType,
    ResourceSearchType searchType,
    String ownerId,
    LocalDate startDate,
    LocalDate endDate,
    FileType fileType,
    ResourceSortField sortBy,
    SortOrder sortOrder,
    Object cursorValue,
    Long cursorId,
    ResourceType cursorType,
    Integer pageSize
)
{
    public ResourceSearchCommand {
        sortOrder = SortOrder.withSortBy(sortBy, sortOrder);
    }
}
