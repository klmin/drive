package com.thinkfree.drive.application.admin.user.info;

import java.util.List;

/** 사용자 일괄 등록/수정 결과 — 웹 응답(AdminUserBulkUpsertResponse)과 필드 동형 */
public record AdminUserBulkUpsertInfo
(
    int insertedCount,
    int updatedCount,
    List<InsertedUserInfo> insertedUsers
)
{
    public record InsertedUserInfo(String userId, int userSeq) {}
}
