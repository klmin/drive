package com.thinkfree.drive.api.page.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PagingRequest
(
    @Schema(title = "Page number", description = "Page to retrieve, starting at 1.")
    @NotNull
    @Min(1)
    Integer pi,
    @Schema(title = "Page size", description = "Number of entries to return per page. Enter an integer of at least 1.")
    @NotNull
    @Min(1)
    Integer ps
)
{
}