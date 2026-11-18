package com.thinkfree.drive.mcp.tool.converter;

import com.thinkfree.drive.application.apikey.info.ApiKeyListInfo;
import com.thinkfree.drive.application.page.CursorPageInfo;
import com.thinkfree.drive.mcp.tool.response.AdminApiKeyListToolResponse;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * application Info → MCP 툴 응답 변환 전담 — 웹 어댑터의 AdminApiKeyResponseConverter 등가물.
 * 항목 필드는 ApiKeyListInfo와 이름이 전부 동일해 명시 매핑이 없다 (내부 API 명세 동형 계약 —
 * Info에만 있는 tenantSeq·tenantName은 응답 record에 필드가 없어 자연 제외).
 * 날짜 String 변환은 ToolDateTimeSupport 공용 (웹 @DriveDateTimeFormat 동일 포맷).
 */
@Mapper(componentModel = "spring")
public interface AdminApiKeyToolConverter extends ToolDateTimeSupport {

    List<AdminApiKeyListToolResponse.AdminApiKeyItem> toListItems(List<ApiKeyListInfo> infos);

    default AdminApiKeyListToolResponse toListResponse(CursorPageInfo<ApiKeyListInfo> pageInfo) {
        return new AdminApiKeyListToolResponse(toListItems(pageInfo.list()), pageInfo.pageSize(),
                pageInfo.nextCursor(), toCursorValue(pageInfo.nextCursorValue()), pageInfo.nextCursorType(), pageInfo.hasNext());
    }

}
