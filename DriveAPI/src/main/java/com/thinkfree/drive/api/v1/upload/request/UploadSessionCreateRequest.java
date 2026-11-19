package com.thinkfree.drive.api.v1.upload.request;

import com.thinkfree.drive.resource.enums.ResourceSaveType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UploadSessionCreateRequest
(
        @Schema(title = "Shared-by user ID", description = "User ID of the Drive that holds the destination (sharedByUserSeq from the shared list). Leave unset for your own Drive.")
        Integer sharedByUserSeq,
        @Schema(title = "Parent folder ID", description = "Destination folder resourceSeq for DUPLICATE. Leave blank to upload to your Drive root.")
        Long parentFolderSeq,
        @Schema(title = "Resource ID", description = "Existing file ID. Required for VERSION or OVERWRITE.")
        Long resourceSeq,
        @Schema(title = "File name", description = "File name including the extension. The server decides the Content-Type from the extension.")
        @NotBlank
        @Size(max = 255)
        String fileName,
        @Schema(title = "Size", description = "Total file size in bytes. Files below the single-request threshold (32MB by default) must use the regular upload API.")
        @NotNull
        @PositiveOrZero
        Long size,
        @Schema(title = "Upload mode", description = "DUPLICATE adds a file (default). VERSION adds a version; OVERWRITE replaces the target file.")
        ResourceSaveType saveType
)
{
}
