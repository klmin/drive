package com.thinkfree.drive.api.v1.admin.user.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;

import java.util.List;

public record AdminUserListRequest(
        @Schema(title = "Account status", description = "Return accounts with the selected status.")
        UserStatus s,
        @Schema(title = "Account email filter", description = "Filter accounts by their sign-in account ID (email address).")
        String u,
        /* 역할 필터(쉼표 구분) — 하나라도 보유하면 포함. USER·ADMIN 만, 생략 시 둘 다 */
        @ArraySchema(arraySchema = @Schema(title = "Account roles", description = "Return accounts holding any selected role. Use USER and/or ADMIN; omitting the filter includes both."), schema = @Schema(title = "Account role", description = "USER or ADMIN role to include."))
        List<RoleType> rt
) {
}
