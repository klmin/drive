package com.thinkfree.drive.mcp.tool.converter;

import com.thinkfree.drive.application.page.OffsetPageInfo;
import com.thinkfree.drive.application.version.info.VersionListInfo;
import com.thinkfree.drive.mcp.tool.response.VersionListToolResponse;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * application Info → MCP 툴 응답 변환 전담 — 웹 어댑터의 ResponseConverter 등가물.
 * 항목 필드는 VersionListInfo와 이름이 전부 동일해 명시 매핑이 없다 (내부 API 명세 동형 계약).
 * 날짜 String 변환은 ToolDateTimeSupport 공용 (웹 @DriveDateTimeFormat 동일 포맷).
 */
@Mapper(componentModel = "spring")
public interface VersionToolConverter extends ToolDateTimeSupport {

    VersionListToolResponse.VersionItem toListItem(VersionListInfo info);

    List<VersionListToolResponse.VersionItem> toListItems(List<VersionListInfo> infos);

    default VersionListToolResponse toListResponse(OffsetPageInfo<VersionListInfo> pageInfo) {
        return new VersionListToolResponse(toListItems(pageInfo.list()),
                pageInfo.totalCount(), pageInfo.pageIndex(), pageInfo.pageSize());
    }

}
