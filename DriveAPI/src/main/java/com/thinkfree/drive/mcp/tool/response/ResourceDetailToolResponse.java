package com.thinkfree.drive.mcp.tool.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.UserStatus;

/**
 * 내부 상세 조회 API 명세와 동형 계약 — 필드는 웹 응답(ResourceDetailResponse) 전체와 동일하다.
 * 봉투({data, error})만 MCP 규약.
 * 날짜는 String — MCP 경로는 Spring AI 내부 ObjectMapper가 직렬화해 웹의 @DriveDateTimeFormat이
 * 적용되지 않으므로(LocalDateTime이면 배열로 나감), 컨버터가 웹과 같은 포맷으로 확정해 넣는다.
 * webViewUrl만 MCP 전용 추가 필드 — 파일은 프런트 미리보기(/preview), 폴더는 폴더 열람(/drive|/shared) 딥링크.
 */
public record ResourceDetailToolResponse
(
    Long resourceSeq,
    ResourceType resourceType,
    Long parentFolderSeq,
    String parentFolderName,
    String resourceName,
    String ext,
    Integer userSeq,
    Integer ownerSeq,
    UserStatus ownerStatus,
    String ownerId,
    String ownerName,
    Integer ownerTenantSeq,
    String ownerTenantName,
    String fullPathSeq,
    String fullPath,
    Long sizeByte,
    Boolean isLocked,
    Boolean canView,
    Boolean canEdit,
    Boolean canDownload,
    Boolean canUpload,
    Boolean canDelete,
    Boolean canShare,
    String resourceRegisterUserId,
    String resourceRegisterUserName,
    UserStatus resourceRegisterUserStatus,
    String resourceRegisterDate,
    String resourceUpdateUserId,
    String resourceUpdateUserName,
    UserStatus resourceUpdateUserStatus,
    String resourceUpdateDate,
    @JsonInclude(JsonInclude.Include.NON_NULL) String webViewUrl
)
{
}
