package com.thinkfree.drive.api.v1.admin.user.pendingdelete.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AdminPendingDeletePermanentRequest
(
    @ArraySchema(arraySchema = @Schema(title = "Users to permanently delete", description = "Add numeric userSeq values of pending-deletion accounts to permanently remove."), schema = @Schema(title = "User ID", description = "Numeric userSeq of one pending-deletion account."))
    @NotEmpty
    List<Integer> ul
)
{
}
