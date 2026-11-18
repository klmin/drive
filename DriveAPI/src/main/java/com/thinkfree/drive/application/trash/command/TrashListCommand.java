package com.thinkfree.drive.application.trash.command;

import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.trash.enums.TrashSortField;
import lombok.Builder;

import java.time.LocalDate;

/**
 * 인자가 많아 위치 기반 생성이 위험하므로 직접 생성하는 어댑터(MCP 툴)는 builder로 조립한다.
 * (웹 어댑터는 MapStruct RequestConverter가 이름 기반으로 생성)
 * startDate/endDate 필터와 REGISTER 정렬의 기준 날짜는 삭제일(TB_TRASH.REGISTER_DATE)이다.
 */
@Builder
public record TrashListCommand
(
    ResourceType resourceType,
    String deleteUserId,
    LocalDate startDate,
    LocalDate endDate,
    FileType fileType,
    TrashSortField sortBy,
    SortOrder sortOrder,
    Object cursorValue,
    Long cursorId,
    ResourceType cursorType,
    Integer pageSize
)
{
    public TrashListCommand {
        sortOrder = SortOrder.withSortBy(sortBy, sortOrder);
    }
}
