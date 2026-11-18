package com.thinkfree.drive.api.v1.resource.version.request;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.PathVariable;

public record ResourceVersionDeleteAllRequest(
        @Schema(title = "Resource ID", description = "Numeric resourceSeq of the file or folder to operate on. Copy it from the file and folder list.")
        @PathVariable
        Long resourceSeq
) {
}
