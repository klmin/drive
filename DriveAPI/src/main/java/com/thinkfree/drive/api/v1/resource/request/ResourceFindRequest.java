package com.thinkfree.drive.api.v1.resource.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.permission.aspect.PermissionAwareResource;
import org.springframework.web.bind.annotation.PathVariable;

public record ResourceFindRequest
(
    @Schema(title = "Resource ID", description = "Numeric resourceSeq of the file or folder to operate on. Copy it from the file and folder list.")
    @PathVariable
    Long resourceSeq,
    @Schema(title = "Shared-by user ID", description = "User ID of the Drive that holds the item: sharedByUserSeq from the shared, starred, recent or pinned-folder list, or userSeq from the file and folder list or detail (not ownerSeq). Leave unset for items in your own Drive.")
    Integer ss,
    @Schema(title = "Look in trash", description = "Set true to look up a deleted item in the trash. Omit or use false for an active item.")
    Boolean d
)
    implements PermissionAwareResource
{
    @Override
    public Integer sharedByUserSeq() {
        return ss;
    }
}
