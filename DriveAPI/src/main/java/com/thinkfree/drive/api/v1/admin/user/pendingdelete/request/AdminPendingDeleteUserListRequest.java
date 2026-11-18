package com.thinkfree.drive.api.v1.admin.user.pendingdelete.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record AdminPendingDeleteUserListRequest
(
    @Schema(title = "Deletion requested from", description = "Start date for when accounts entered pending deletion, in YYYY-MM-DD format.")
    LocalDate pdSd, // pending delete start date
    @Schema(title = "Deletion requested through", description = "End date for when accounts entered pending deletion, in YYYY-MM-DD format.")
    LocalDate pdEd, // pending delete end date
    @Schema(title = "Scheduled deletion from", description = "Start date of the scheduled account-deletion range, in YYYY-MM-DD format.")
    LocalDate dsSd, // delete schedule start date
    @Schema(title = "Scheduled deletion through", description = "End date of the scheduled account-deletion range, in YYYY-MM-DD format.")
    LocalDate dsEd  // delete schedule end date
)
{
}
