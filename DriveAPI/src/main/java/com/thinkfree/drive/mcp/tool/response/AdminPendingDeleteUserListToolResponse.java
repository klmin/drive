package com.thinkfree.drive.mcp.tool.response;

import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.util.List;

/**
 * 내부 관리자 삭제 대기 사용자 목록 API 명세와 동형 계약 — 항목 필드는 웹 응답(AdminPendingDeleteUserListResponse) 전체와 동일하고,
 * 커서 필드도 명세 명명(nextCursor·nextCursorValue) 그대로 평면 배치한다. 봉투({data, error})만 MCP 규약.
 * 날짜는 String — ToolDateTimeSupport 참고. 커서가 pendingDeleteDate DESC(값) + userSeq DESC(id) 복합 기준이라
 * 다음 페이지 요청 시 nextCursor(→cursorId)와 nextCursorValue(→cursorValue)를 둘 다 넘겨야 한다 (웹 계약 동일).
 * deleteUser*는 탈퇴 처리를 수행한 관리자 정보.
 */
public record AdminPendingDeleteUserListToolResponse
(
    List<PendingDeleteUserItem> users,
    int pageSize,
    Number nextCursor,
    Object nextCursorValue,
    ResourceType nextCursorType,
    boolean hasNext
)
{
    public record PendingDeleteUserItem(
            Integer userSeq,
            String userId,
            String userName,
            UserStatus status,
            List<RoleType> roles,
            String pendingDeleteDate,
            String deleteScheduleTime,
            Integer deleteUserSeq,
            String deleteUserId,
            UserStatus deleteUserStatus
    )
    {
    }
}
