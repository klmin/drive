package com.thinkfree.drive.api.v1.admin.branding.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.branding.enums.BrandingImageSource;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.RequestParam;

public record BrandingImageRequest
(
    @Schema(title = "Branding image source", description = "CUSTOM returns the uploaded branding image; DEFAULT returns the built-in image.")
    @NotNull
    @RequestParam
    BrandingImageSource source
)
{
}
