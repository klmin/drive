package com.thinkfree.drive.mcp.tool.converter;

import com.thinkfree.drive.application.document.info.DocumentCreateInfo;
import com.thinkfree.drive.application.file.info.FileContentUploadInfo;
import com.thinkfree.drive.application.file.info.FileCopyInfo;
import com.thinkfree.drive.application.file.info.FileDownloadContentInfo;
import com.thinkfree.drive.application.file.info.FileReadInfo;
import com.thinkfree.drive.mcp.tool.response.FileCopyToolResponse;
import com.thinkfree.drive.mcp.tool.response.FileCreateToolResponse;
import com.thinkfree.drive.mcp.tool.response.FileDownloadToolResponse;
import com.thinkfree.drive.mcp.tool.response.FileReadToolResponse;
import com.thinkfree.drive.mcp.tool.response.FileUploadToolResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * application Info → MCP 툴 응답 변환 전담 — 웹 어댑터의 FileResponseConverter 등가물.
 * 필드는 Info와 이름이 전부 동일해 명시 매핑이 없다 (내부 API 명세 동형 계약).
 */
@Mapper(componentModel = "spring")
public interface FileToolConverter {

    FileCopyToolResponse toCopyResponse(FileCopyInfo info);

    // webViewUrl/editUrl 조립은 어댑터(툴) 책임 — Info의 ownerUserSeq는 URL 재료로만 쓰이고 응답엔 싣지 않는다.
    // editUrl은 편집 불가(형식·권한)면 null로 들어와 NON_NULL 직렬화로 필드째 생략된다
    @Mapping(target = "webViewUrl", source = "webViewUrl")
    @Mapping(target = "editUrl", source = "editUrl")
    FileReadToolResponse toReadResponse(FileReadInfo info, String webViewUrl, String editUrl);

    @Mapping(target = "webViewUrl", source = "webViewUrl")
    FileDownloadToolResponse toDownloadResponse(FileDownloadContentInfo info, String webViewUrl);

    @Mapping(target = "webViewUrl", source = "webViewUrl")
    FileCreateToolResponse toCreateFileResponse(DocumentCreateInfo info, String webViewUrl);

    @Mapping(target = "webViewUrl", source = "webViewUrl")
    @Mapping(target = "editUrl", source = "editUrl")
    FileUploadToolResponse toUploadResponse(FileContentUploadInfo info, String webViewUrl, String editUrl);


}
