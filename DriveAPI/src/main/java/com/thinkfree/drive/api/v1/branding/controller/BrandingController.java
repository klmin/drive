package com.thinkfree.drive.api.v1.branding.controller;

import com.thinkfree.drive.api.v1.branding.response.BrandingMetaResponse;
import com.thinkfree.drive.api.v1.branding.usecase.BrandingUseCase;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Branding")
@RestController
@RequestMapping("/api/v1/branding")
@RequiredArgsConstructor
public class BrandingController {

    private final BrandingUseCase brandingUseCase;

    @ExternalApi
    @Operation(summary = "Get Drive branding",
            description = "Returns branding metadata for the current tenant and supplies defaults when custom branding is absent.")
    @GetMapping
    public ResponseEntity<BrandingMetaResponse> getConfig() {
        var response = brandingUseCase.getMeta();
        return ResponseEntity.ok(response);
    }
}
