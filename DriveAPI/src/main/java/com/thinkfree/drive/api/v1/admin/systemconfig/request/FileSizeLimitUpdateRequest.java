package com.thinkfree.drive.api.v1.admin.systemconfig.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

public record FileSizeLimitUpdateRequest(
    @Schema(title = "Maximum file size (bytes)", description = "Maximum size of one uploaded file, in bytes. A specified limit must be positive.")
    @Positive(message = "파일 업로드 용량 제한은 양수여야 합니다.")
    Long maxFileUploadSize
) {
}
