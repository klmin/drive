package com.thinkfree.drive.api.v1.file.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.permission.aspect.PermissionAwareResource;
import org.springframework.web.bind.annotation.PathVariable;

public record FileDownloadRequest
(
    @Schema(title = "Resource ID", description = "Numeric resourceSeq of the file or folder to operate on. Copy it from the file and folder list.")
    @PathVariable
    Long resourceSeq,
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
    public Long resourceSeq(){
        return resourceSeq;
    }
}
