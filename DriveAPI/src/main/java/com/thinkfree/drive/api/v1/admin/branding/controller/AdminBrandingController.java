package com.thinkfree.drive.api.v1.admin.branding.controller;

import com.thinkfree.drive.global.annotation.ParameterDoc;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.branding.request.BrandingImageRequest;
import com.thinkfree.drive.api.v1.admin.branding.request.BrandingUpsertRequest;
import com.thinkfree.drive.api.v1.admin.branding.response.BrandingSettingResponse;
import com.thinkfree.drive.api.v1.admin.branding.usecase.AdminBrandingUseCase;
import com.thinkfree.drive.branding.enums.BrandingImageType;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Branding")
@RestController
@RequestMapping("/api/v1/admins/branding")
@RequiredArgsConstructor
public class AdminBrandingController {

    private final AdminBrandingUseCase adminBrandingUseCase;

    @ExternalApi
    @Operation(summary = "Get tenant branding settings",
            description = "Returns the current branding configuration for the tenant, including the configured image references.")
    @GetMapping
    public ResponseEntity<ApiResponse<BrandingSettingResponse>> find() {
        var response = adminBrandingUseCase.get();
        return ApiResponse.success(response);
    }

    @ExternalApi
    @Operation(summary = "Save tenant branding settings",
            description = "Creates or updates the tenant's branding configuration using multipart form data for settings and image uploads.")
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> upsert(@ModelAttribute @Validated(BrandingUpsertRequest.ValidationOrder.class) BrandingUpsertRequest request)  {
        adminBrandingUseCase.upsert(request);
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Preview a branding image",
            description = "Select source=custom to retrieve the stored image or source=default to retrieve the default. A missing custom image returns 404.")
    @GetMapping("/images/{type}")
    public ResponseEntity<byte[]> getImage(@ParameterDoc(title = "Branding image type", description = "Choose the favicon, compact logo, full logo or email logo to retrieve.") @PathVariable BrandingImageType type, @Valid @ModelAttribute BrandingImageRequest request) {
        var result = adminBrandingUseCase.getImage(type, request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType()))
                .contentLength(result.contentLength())
                .body(result.imageData());
    }
}
