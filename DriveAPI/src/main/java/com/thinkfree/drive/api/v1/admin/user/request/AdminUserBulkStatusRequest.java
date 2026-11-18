package com.thinkfree.drive.api.v1.admin.user.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AdminUserBulkStatusRequest(
        @ArraySchema(arraySchema = @Schema(title = "Account emails", description = "Add the sign-in account IDs (email addresses) whose status you want to check."), schema = @Schema(title = "Account email", description = "Sign-in email address of one account to check."))
        @NotEmpty
        List<String> userIds
) {
}
