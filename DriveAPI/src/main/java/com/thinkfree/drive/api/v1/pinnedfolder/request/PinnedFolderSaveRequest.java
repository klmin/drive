package com.thinkfree.drive.api.v1.pinnedfolder.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.permission.aspect.PermissionAwareResource;
import jakarta.validation.constraints.NotNull;

public record PinnedFolderSaveRequest
(
    @Schema(title = "Resource ID", description = "Numeric resourceSeq of the folder to pin, returned by the file and folder list. Only folders can be pinned, up to 3.")
    @NotNull
    Long rs,
    @Schema(title = "Shared-by user ID", description = "User ID of the Drive that holds the item: sharedByUserSeq from the shared, starred, recent or pinned-folder list, or userSeq from the file and folder list or detail (not ownerSeq). Leave unset for items in your own Drive.")
    Integer ss
)
    implements PermissionAwareResource
{
    @Override
    public Integer sharedByUserSeq() {
            return ss;
    }
    
    @Override
    public Long resourceSeq() {
            return rs;
    }
}
