package com.thinkfree.drive.api.v1.share.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.permission.aspect.PermissionAwareShare;
import org.springframework.web.bind.annotation.PathVariable;

public record SharePathRequest
(
        @Schema(title = "Share ID", description = "Numeric shareSeq returned by the share list. This identifies the sharing record, not the file.")
        @PathVariable
        Long shareSeq,
        @Schema(title = "Shared-by user ID", description = "User ID of the Drive that holds the item: sharedByUserSeq from the shared, starred, recent or pinned-folder list, or userSeq from the file and folder list or detail (not ownerSeq). Leave unset for items in your own Drive.")
        Integer ss
)
        implements PermissionAwareShare
{
        
        @Override
        public Integer sharedByUserSeq() {
                return ss;
        }
}

