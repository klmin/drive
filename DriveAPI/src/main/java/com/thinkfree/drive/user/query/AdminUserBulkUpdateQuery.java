package com.thinkfree.drive.user.query;

import com.thinkfree.drive.user.admin.dto.AdminUserBulkUpdateDto;
import com.thinkfree.drive.user.enums.RoleType;

/**
 * 관리자 일괄 수정 배치 UPDATE 한 행의 파라미터 — {@code UserRepository#updateAll}.
 * 프로필 이미지는 건드리지 않는다. 수정자·수정일은 리포가 {@code JdbcAudit} 로 채운다(시큐리티 사용자·현재 시각 — 구 AdminUserUpdateQuery 도 호출부가 비워 넘김).
 */
public record AdminUserBulkUpdateQuery(
        Integer userSeq,
        String userName,
        Long totalQuotaByte,
        String countryCode,
        String phoneNumber
) {

    public static AdminUserBulkUpdateQuery from(AdminUserBulkUpdateDto dto) {
        return new AdminUserBulkUpdateQuery(dto.userSeq(), dto.userName(), dto.totalQuotaByte(), dto.countryCode(), dto.phoneNumber());
    }
}
