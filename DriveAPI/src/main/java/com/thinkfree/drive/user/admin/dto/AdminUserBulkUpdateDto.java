package com.thinkfree.drive.user.admin.dto;

import com.thinkfree.drive.user.enums.RoleType;

/**
 * 관리자 일괄 수정(bulkUpsert 의 update 행) service 입력 — AdminUserService.upsertAll.
 * service 가 {@code AdminUserBulkUpdateQuery} 로 바꿔 리포 배치 UPDATE 에 넘긴다(프로필 이미지는 그대로 — B1).
 */
public record AdminUserBulkUpdateDto
(
    Integer userSeq,
    String userName,
    Long totalQuotaByte,
    String countryCode,
    String phoneNumber
)
{
}
