package com.thinkfree.drive.mcp.tool.response;

import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.util.List;

/**
 * 내부 관리자 사용자 검색 API 명세와 동형 계약 — 항목 필드는 웹 응답(AdminUserSearchResponse) 전체와 동일하다.
 * 봉투({data, error})만 MCP 규약. 검색 결과는 커서 없는 전체 목록(웹 계약 동일).
 */
public record AdminUserSearchToolResponse
(
    List<AdminUserSearchItem> users
)
{
    public record AdminUserSearchItem(
            Integer userSeq,
            String userId,
            String userName,
            UserStatus status,
            List<RoleType> roles,
            Boolean isProtected
    )
    {
    }
}
