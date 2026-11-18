package com.thinkfree.drive.api.v1.admin.user.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record AdminUserSearchRequest(
        /* userId·userName 중 하나만 걸려도 검색된다 */
        @Schema(title = "Account search keyword", description = "Match the account's sign-in ID or display name.")
        @NotBlank
        String sk,
        /* 미지정 시 USER 만. ADMIN·USER 만 지정할 수 있다. 여러 개면 하나라도 보유한 계정 */
        @ArraySchema(arraySchema = @Schema(title = "Account roles", description = "Find accounts holding any selected USER or ADMIN role. Defaults to USER when omitted."), schema = @Schema(title = "Account role", description = "USER or ADMIN role to include."))
        List<RoleType> rt,
        /* 미지정 시 ACTIVE 만. 탈퇴·삭제대기 계정까지 찾으려면 명시해야 한다 */
        @ArraySchema(arraySchema = @Schema(title = "Account statuses", description = "Statuses to search. Defaults to ACTIVE; explicitly add other statuses to search inactive or pending-deletion accounts."), schema = @Schema(title = "Account status", description = "Account status to include in the search."))
        List<UserStatus> st
) {
}
