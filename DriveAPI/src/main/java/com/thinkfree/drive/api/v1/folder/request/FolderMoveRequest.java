package com.thinkfree.drive.api.v1.folder.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record FolderMoveRequest
(
    @Schema(title = "Shared-by user ID", description = "User ID of the Drive that holds the item: sharedByUserSeq from the shared, starred, recent or pinned-folder list, or userSeq from the file and folder list or detail (not ownerSeq). Leave unset for items in your own Drive.")
    Integer ss,
    @Schema(title = "Destination user ID", description = "Numeric user ID of the destination Drive. Omit to use your own Drive.")
    Integer ts,
    @Schema(title = "Destination folder ID", description = "Destination folder resourceSeq. Leave blank to move to your Drive root. For a shared destination, enter its folder ID and ts.")
    Long pfs
)
{
}
