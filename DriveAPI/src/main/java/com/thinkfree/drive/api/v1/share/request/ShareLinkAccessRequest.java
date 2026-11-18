package com.thinkfree.drive.api.v1.share.request;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.PathVariable;

public record ShareLinkAccessRequest
(
    @Schema(title = "Share-link token", description = "Token from the sharing link you want to access.")
    @PathVariable
    String token
)
{
}
