package com.thinkfree.drive.api.v1.file.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.resource.enums.NameConflictPolicy;
import jakarta.validation.constraints.NotBlank;

public record FileRenameRequest
(
    @Schema(title = "Shared-by user ID", description = "User ID of the Drive that holds the item: sharedByUserSeq from the shared, starred, recent or pinned-folder list, or userSeq from the file and folder list or detail (not ownerSeq). Leave unset for items in your own Drive.")
    Integer ss,
    @Schema(title = "New file name", description = "New file name without a parent-folder path. Drive returns the applied name without the extension. The name cannot start or end with a space or a period, exceed 255 characters including the extension, or contain \\ / : * ? \" < > |. The part before the extension cannot be empty, end with a space, or be a Windows reserved name (CON, PRN, AUX, NUL, COM1-COM9, LPT1-LPT9).")
    @NotBlank
    String rn,
    @Schema(title = "Duplicate-name handling", description = "NONE rejects an existing name; AUTO adds a number to make the name unique.")
    NameConflictPolicy ncp
)
{
}
