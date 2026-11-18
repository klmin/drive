package com.thinkfree.drive.api.v1.trash.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record TrashFileRestoreRequest
(
    @Schema(title = "Restore to original folder", description = "Use true to restore to the original parent folder, or false to restore to the Drive root.")
    @NotNull
    Boolean pe
)
{
}
