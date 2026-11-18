package com.thinkfree.drive.api.page.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PaginationRequest
(
    @NotNull
    @Min(1)
    Integer pageIndex,
    @NotNull
    @Min(1)
    Integer pageSize
)
{
}