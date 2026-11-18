package com.thinkfree.drive.mcp.tool.response;

import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.util.List;

/**
 * 내부 관리자 사용자 목록 API 명세와 동형 계약 — 항목 필드는 웹 응답(AdminUserListResponse) 전체와 동일하고,
 * 커서 필드도 명세 명명(nextCursor) 그대로 평면 배치한다. 봉투({data, error})만 MCP 규약.
 * 날짜는 String — ToolDateTimeSupport 참고. 커서가 userSeq 단일 기준이라
 * nextCursorValue/nextCursorType은 항상 null이다 (웹 계약 동일 — 페이지 이동은 nextCursor만 쓴다).
 */
public record AdminUserListToolResponse
(
    List<AdminUserItem> users,
    int pageSize,
    Number nextCursor,
    Object nextCursorValue,
    ResourceType nextCursorType,
    boolean hasNext
)
{
    public record AdminUserItem(
            Integer userSeq,
            String userId,
            String userName,
            UserStatus status,
            List<RoleType> roles,
            Integer registerUserSeq,
            String registerDate,
            Integer updateUserSeq,
            String updateDate,
            String lastLoginDate,
            Boolean isProtected
    )
    {
    }
}
