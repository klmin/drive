package com.thinkfree.drive.api.v1.admin.user.response;

import java.util.List;

public record AdminUserBulkUpsertResponse
(
    int insertedCount,
    int updatedCount,
    List<InsertedUser> insertedUsers
)
{
    public record InsertedUser(String userId, int userSeq) {}
}
