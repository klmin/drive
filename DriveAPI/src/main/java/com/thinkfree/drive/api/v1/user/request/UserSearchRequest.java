package com.thinkfree.drive.api.v1.user.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UserSearchRequest
(
    @Schema(title = "Search keyword", description = "Text used to filter the matching entries.")
    @NotBlank
    String sk
)
{
}
