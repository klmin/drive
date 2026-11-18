package com.thinkfree.drive.api.v1.superadmin.user.request;

import com.thinkfree.drive.user.enums.RoleType;
import jakarta.validation.constraints.NotBlank;

public record SuperAdminUserSearchRequest(
        /* userId·userName 중 하나만 걸려도 검색된다 */
        @NotBlank
        String sk,
        /* 미지정 시 ADMIN·USER 전체. ADMIN 또는 USER 만 지정할 수 있다 */
        RoleType rt
) {
}
