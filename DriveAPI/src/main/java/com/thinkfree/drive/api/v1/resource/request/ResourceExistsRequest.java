package com.thinkfree.drive.api.v1.resource.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.resource.enums.ResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ResourceExistsRequest
(
    @Schema(title = "Shared-by user ID", description = "User ID of the Drive that holds the item: sharedByUserSeq from the shared, starred, recent or pinned-folder list, or userSeq from the file and folder list or detail (not ownerSeq). Leave unset for items in your own Drive.")
    Integer ss,
    @Schema(title = "Parent folder ID", description = "Parent folder resourceSeq for the name check. Leave blank to check your Drive root. For a shared folder, enter its folder ID and ss.")
    Long pfs,
    @Schema(title = "Name to check", description = "Exact file or folder name to look for in the specified parent folder.")
    @NotBlank
    String rn,
    @Schema(title = "Resource type", description = "Choose FILE to check a file name or FOLDER to check a folder name.")
    @NotNull
    ResourceType rt
)
{
}
