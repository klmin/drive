package com.thinkfree.drive.api.v1.folder.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.permission.aspect.PermissionAwareResource;
import jakarta.validation.constraints.NotBlank;

public record FolderUploadRequest(
        @Schema(title = "Shared-by user ID", description = "User ID of the Drive that holds the item: sharedByUserSeq from the shared, starred, recent or pinned-folder list, or userSeq from the file and folder list or detail (not ownerSeq). Leave unset for items in your own Drive.")
        Integer ss,
        @Schema(title = "Parent folder ID", description = "Parent folder resourceSeq. Leave blank to create the folder in your Drive root. For a shared folder, enter its folder ID and ss.")
        Long pfs,
        @Schema(title = "Folder name", description = "Name of the new folder, without a path. It cannot start or end with a space or a period, exceed 255 characters, contain \\ / : * ? \" < > |, or be a Windows reserved name (CON, PRN, AUX, NUL, COM1-COM9, LPT1-LPT9).")
        @NotBlank
        String fn
)
        implements PermissionAwareResource
{
        @Override
        public Integer sharedByUserSeq() {
                return ss;
        }
        
        @Override
        public Long resourceSeq() {
                return pfs;
        }
}
