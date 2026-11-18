package com.thinkfree.drive.api.v1.admin.sharesetting.controller;

import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.sharesetting.request.AdminShareSettingSaveRequest;
import com.thinkfree.drive.api.v1.admin.sharesetting.response.AdminShareSettingResponse;
import com.thinkfree.drive.api.v1.admin.sharesetting.usecase.AdminShareSettingUseCase;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantRestriction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Sharing settings")
@RestController
@RequestMapping("/api/v1/admins/share-settings")
@RequiredArgsConstructor
public class AdminShareSettingController {

    private final AdminShareSettingUseCase adminShareSettingUseCase;

    @ExternalApi
    @Operation(summary = "Get tenant sharing settings",
            description = "Returns the sharing rules configured for the current tenant.")
    @GetMapping
    public ResponseEntity<ApiResponse<AdminShareSettingResponse>> find() {
        var response = adminShareSettingUseCase.get();
        return ApiResponse.success(response);
    }

    @ExternalApi
    @TenantRestriction(TenantFeature.SHARE)
    @Operation(summary = "Update tenant sharing settings",
            description = "Saves the supplied sharing rules for the current tenant.")
    @PatchMapping
    public ResponseEntity<ApiResponse<Void>> save(@Valid @RequestBody AdminShareSettingSaveRequest request) {
        adminShareSettingUseCase.save(request);
        return ApiResponse.success();
    }
}
