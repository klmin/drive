package com.thinkfree.drive.mcp.tool.response;

import java.util.List;

/**
 * 내부 관리자 사용자 일괄 등록/수정 API(POST /admins/users/bulk) 명세와 동형 계약 — 필드는 웹 응답(AdminUserBulkUpsertResponse)과 동일하다.
 * 전체가 단일 트랜잭션이라 성공 시에만 반환된다(부분 성공 없음). insertedUsers는 신규 등록된 계정만 담고 수정 항목은 updatedCount로만 집계된다.
 */
public record AdminUserBulkUpsertToolResponse
(
    int insertedCount,
    int updatedCount,
    List<InsertedUser> insertedUsers
)
{
    public record InsertedUser(String userId, int userSeq) {}
}
