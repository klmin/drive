package com.thinkfree.drive.mcp.tool.response;

/**
 * 이름변경 결과 — 실제 저장된 이름 전체(파일은 확장자 포함, AUTO 번호 반영). 웹 파일 응답(FileRenameResponse{name})은
 * 확장자를 뺀 이름이라 값이 다르며, 툴은 upload_file·create_folder·metadata와 같은 resourceName 규약(저장된 이름 전체)을 따른다.
 * 웹 폴더 응답은 본문이 없지만 툴은 파일/폴더 통합이라 폴더도 반환.
 */
public record ResourceRenameToolResponse
(
    String resourceName
)
{
}
