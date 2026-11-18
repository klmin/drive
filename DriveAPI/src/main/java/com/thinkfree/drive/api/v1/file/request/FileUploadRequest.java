package com.thinkfree.drive.api.v1.file.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public record FileUploadRequest
(
        @Schema(title = "Shared-by user ID", description = "User ID of the Drive that holds the item: sharedByUserSeq from the shared, starred, recent or pinned-folder list, or userSeq from the file and folder list or detail (not ownerSeq). Leave unset for items in your own Drive.")
        Integer ss,
        @Schema(title = "Parent folder ID", description = "Destination folder resourceSeq for DUPLICATE. Leave blank to upload to your Drive root. For a shared folder, enter its folder ID and ss.")
        Long pfs,
        @Schema(title = "Resource ID", description = "Existing file ID. Required for VERSION or OVERWRITE.")
        Long rs,
        @Schema(title = "File", description = "Choose the local file to upload.")
        @NotNull
        MultipartFile file,
        @Schema(title = "Upload mode", description = "DUPLICATE adds a file (default). VERSION adds a version; OVERWRITE replaces the target file.")
        ResourceSaveType st
)
{
}
