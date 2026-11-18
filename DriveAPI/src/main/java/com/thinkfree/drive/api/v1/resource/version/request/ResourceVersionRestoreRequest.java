package com.thinkfree.drive.api.v1.resource.version.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record ResourceVersionRestoreRequest
(
        @Schema(title = "Shared-by user ID", description = "User ID of the Drive that holds the item: sharedByUserSeq from the shared, starred, recent or pinned-folder list, or userSeq from the file and folder list or detail (not ownerSeq). Leave unset for items in your own Drive.")
        Integer ss
)
{
}

