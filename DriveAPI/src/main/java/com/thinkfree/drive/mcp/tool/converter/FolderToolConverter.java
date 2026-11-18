package com.thinkfree.drive.mcp.tool.converter;

import com.thinkfree.drive.application.folder.info.FolderCreateInfo;
import com.thinkfree.drive.mcp.tool.response.FolderCreateToolResponse;
import org.mapstruct.Mapper;

/**
 * application Info → MCP 툴 응답 변환 전담 — 웹 어댑터의 FolderResponseConverter 등가물.
 * 필드는 FolderCreateInfo와 이름이 전부 동일해 명시 매핑이 없다 (내부 API 명세 동형 계약).
 */
@Mapper(componentModel = "spring")
public interface FolderToolConverter {

    FolderCreateToolResponse toCreateResponse(FolderCreateInfo info);

}
