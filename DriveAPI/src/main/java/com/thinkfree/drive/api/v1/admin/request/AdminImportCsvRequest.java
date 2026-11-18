package com.thinkfree.drive.api.v1.admin.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public record AdminImportCsvRequest
(
    @Schema(title = "File", description = "Choose the local CSV file containing the user accounts to import.")
    @NotNull
    MultipartFile file,
    @Schema(title = "Send notification email", description = "Send an email notification for this operation when true.")
    Boolean se
)
{
}
