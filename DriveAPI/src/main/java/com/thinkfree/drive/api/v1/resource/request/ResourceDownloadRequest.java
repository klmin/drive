package com.thinkfree.drive.api.v1.resource.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.partition.PartitionKeyProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.LinkedHashSet;

public record ResourceDownloadRequest
(
        @Schema(title = "Download items", description = "Files or folders to include in the download. Each entry needs its resource ID (rs) and the user ID of the Drive that holds it (ss).")
        @NotEmpty
        @Valid
        LinkedHashSet<FileDownloadItem> rl
)
{
        public record FileDownloadItem(
                @Schema(title = "Shared-by user ID", description = "Required. User ID of the Drive that holds the item: sharedByUserSeq from the shared, starred, recent or pinned-folder list, or userSeq from the file and folder list or detail (not ownerSeq). For items in your own Drive, use your own user ID.")
                @NotNull
                Integer ss,
                @Schema(title = "Resource ID", description = "Numeric resourceSeq of the file or folder, returned by the file and folder list.")
                @NotNull
                Long rs
        )
                implements PartitionKeyProvider
        {
                @Override
@Schema(hidden = true)
                public Integer getSharedByUserSeq() {
                        return ss;
                }
                
                @Override
@Schema(hidden = true)
                public Long getResourceSeq() {
                        return rs;
                }
        }
}
