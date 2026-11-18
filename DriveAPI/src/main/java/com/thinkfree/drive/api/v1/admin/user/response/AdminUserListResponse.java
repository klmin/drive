package com.thinkfree.drive.api.v1.admin.user.response;

import com.thinkfree.drive.global.time.DriveIsoLocalDateTimeFormat;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.time.Instant;
import java.util.List;

public record AdminUserListResponse(
        Integer userSeq,
        String userId,
        
        String userName,
        UserStatus status,
        List<RoleType> roles,
        Integer registerUserSeq,
        @DriveIsoLocalDateTimeFormat
        Instant registerDate,
        Integer updateUserSeq,
        @DriveIsoLocalDateTimeFormat
        Instant updateDate,
        @DriveIsoLocalDateTimeFormat
        Instant lastLoginDate,
        /* 보호 계정 — 테넌트 유형과 관계없이 탈퇴·삭제·비활성·역할 변경·관리자의 비밀번호 변경이 거부된다(USER_034). 본인 비밀번호 변경은 SYSTEM_MANAGED 테넌트에서만 거부(TENANT_011) */
        Boolean isProtected

) {
}
