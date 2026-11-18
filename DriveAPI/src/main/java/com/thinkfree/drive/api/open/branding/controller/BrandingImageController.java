package com.thinkfree.drive.api.open.branding.controller;

import com.thinkfree.drive.api.open.branding.usecase.BrandingImageUseCase;
import com.thinkfree.drive.branding.enums.BrandingImageType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Branding")
@RestController
@RequestMapping("/branding/images")
@RequiredArgsConstructor
public class BrandingImageController {

    private final BrandingImageUseCase brandingImageUseCase;

    @Operation(summary = "Get a branding image",
            description = "Returns the tenant's branding image. type: favicon | logo-icon | logo-image | email-logo. Supports If-None-Match.")
    @GetMapping("/{type}")
    public ResponseEntity<byte[]> getImage(@PathVariable BrandingImageType type, @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        
        var result = brandingImageUseCase.getImage(type, ifNoneMatch);

        if (result.notModified()) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(result.etag())
                    .build();
        }
        
        return ResponseEntity.ok()
                .eTag(result.etag())
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.parseMediaType(result.contentType()))
                .contentLength(result.contentLength())
                .body(result.data());
    }
}
