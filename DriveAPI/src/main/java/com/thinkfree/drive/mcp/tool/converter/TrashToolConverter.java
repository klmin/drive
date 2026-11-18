package com.thinkfree.drive.mcp.tool.converter;

import com.thinkfree.drive.application.page.CursorPageInfo;
import com.thinkfree.drive.application.trash.info.TrashListInfo;
import com.thinkfree.drive.mcp.tool.response.TrashListToolResponse;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * application Info → MCP 툴 응답 변환 전담 — 웹 어댑터의 ResponseConverter 등가물.
 * 항목 필드는 TrashListInfo와 이름이 전부 동일해 명시 매핑이 없다 (내부 API 명세 동형 계약).
 * 날짜 String 변환은 ToolDateTimeSupport 공용 (웹 @DriveDateTimeFormat 동일 포맷).
 */
@Mapper(componentModel = "spring")
public interface TrashToolConverter extends ToolDateTimeSupport {

    TrashListToolResponse.TrashItem toListItem(TrashListInfo info);

    List<TrashListToolResponse.TrashItem> toListItems(List<TrashListInfo> infos);

    default TrashListToolResponse toListResponse(CursorPageInfo<TrashListInfo> pageInfo) {
        return new TrashListToolResponse(toListItems(pageInfo.list()), pageInfo.pageSize(),
                pageInfo.nextCursor(), toCursorValue(pageInfo.nextCursorValue()), pageInfo.nextCursorType(), pageInfo.hasNext());
    }

}
