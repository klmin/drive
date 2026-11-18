package com.thinkfree.drive.api.v1.document.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.document.enums.DocumentTemplateType;
import jakarta.validation.constraints.NotNull;

public record DocumentCreateRequest
(
    @Schema(title = "Shared-by user ID", description = "User ID of the Drive that holds the item: sharedByUserSeq from the shared, starred, recent or pinned-folder list, or userSeq from the file and folder list or detail (not ownerSeq). Leave unset for items in your own Drive.")
    Integer ss,
    @Schema(title = "Parent folder ID", description = "Parent folder resourceSeq. Leave blank to create the document in your Drive root. For a shared folder, enter its folder ID and ss.")
    Long pfs,
    @Schema(title = "Document template", description = "Choose WORD, EXCEL, PPT or NOTE for the new document.")
    @NotNull
    DocumentTemplateType tt
)
{
}
