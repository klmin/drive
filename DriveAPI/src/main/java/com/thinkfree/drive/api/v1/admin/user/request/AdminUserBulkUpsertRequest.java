package com.thinkfree.drive.api.v1.admin.user.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.user.enums.RoleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/**
 * 순수 웹 입력 DTO — insert/update 분리·정규화·필수값 검증은
 * application의 AdminUserBulkUpsertCommand(진입점 공용)가 담당한다.
 */
public record AdminUserBulkUpsertRequest(
        @Schema(title = "Accounts", description = "Add one entry for each account to create or update.")
        @NotEmpty
        @Valid
        List<UserItem> users
) {
    public record UserItem(
            @Schema(title = "Existing user ID", description = "Set the numeric user ID to update an account. Leave unset to create an account.")
            Integer us,           // userSeq (null=insert, present=update)
            @Schema(title = "Account email", description = "Sign-in email address. Required when creating an account.")
            String ui,            // userId (insert 시 필수)
            @Schema(title = "Password", description = "Password for the account. Required when creating; use 8–64 characters with uppercase, lowercase, a number and a symbol.")
            String encPwd,        // encPwd (insert 시 필수)
            @Schema(title = "Display name", description = "Name that Drive displays for this user.")
            @NotBlank
            String un,            // userName
            @Schema(title = "Storage quota (bytes)", description = "Total storage allowance for this user in bytes. USER accounts require a positive quota; ADMIN-only accounts use no user quota.")
            @PositiveOrZero
            Long tq,              // totalQuotaByte
            @ArraySchema(arraySchema = @Schema(title = "Account roles", description = "USER and/or ADMIN roles for the account. Required when creating a new account."), schema = @Schema(title = "Account role", description = "USER or ADMIN. Tenant-admin APIs do not grant SUPER_ADMIN or ANONYMOUS."))
            List<RoleType> roles, // 역할(USER·ADMIN) — insert 시 필수, update 는 생략 시 유지
            @Schema(title = "Country calling code", description = "Country calling code for the phone number. Send this together with pn, or omit both.")
            String ct,            // countryCode
            @Schema(title = "Phone number", description = "User's phone number. Send this together with the country calling code ct, or omit both.")
            String pn             // phoneNumber
    ) {
    }
}
