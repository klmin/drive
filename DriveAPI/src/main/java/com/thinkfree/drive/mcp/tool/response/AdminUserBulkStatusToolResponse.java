package com.thinkfree.drive.mcp.tool.response;

import com.thinkfree.drive.user.enums.UserStatus;

/**
 * 내부 관리자 사용자 상태 일괄 조회 API(POST /admins/users/bulk-status) 명세와 동형 계약 — 필드는 웹 응답(AdminUserBulkStatusResponse)과 동일하다.
 * 웹이 항목 배열을 그대로 반환하므로 MCP도 List 그대로 data에 담는다({data:[...]}) — 다른 목록 툴의 users 래퍼를 쓰지 않는 유일한 예외(사용자 결정 09-09: 웹 요청·응답 완전 동형).
 * 조회된 계정만 담긴다 — 요청한 userId 중 응답에 없는 것은 존재하지 않거나 관리자(ADMIN) 계정이거나 삭제 대기/삭제 완료 상태(SQL이 USER·ACTIVE/INACTIVE만 반환).
 */
public record AdminUserBulkStatusToolResponse
(
    Integer userSeq,
    String userId,
    UserStatus status
)
{
}
