package com.thinkfree.drive.api.v1.superadmin.sharesetting.controller;

import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.systemconfig.response.ShareExistsResponse;
import com.thinkfree.drive.api.v1.superadmin.sharesetting.request.SuperAdminShareSettingSaveRequest;
import com.thinkfree.drive.api.v1.superadmin.sharesetting.response.SuperAdminShareSettingResponse;
import com.thinkfree.drive.api.v1.superadmin.sharesetting.usecase.SuperAdminShareSettingUseCase;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Super admin - Sharing settings")
@RestController
@RequestMapping("/api/v1/super-admin/share-settings")
@RequiredArgsConstructor
public class SuperAdminShareSettingController {

    private final SuperAdminShareSettingUseCase superAdminShareSettingUseCase;

    @ExternalApi
    @Operation(summary = "Get installation sharing settings",
            description = "Returns the installation-wide sharing configuration.")
    @GetMapping
    public ResponseEntity<ApiResponse<SuperAdminShareSettingResponse>> find() {
        var response = superAdminShareSettingUseCase.get();
        return ApiResponse.success(response);
    }

    @ExternalApi
    @Operation(summary = "Save installation sharing settings",
            description = "Creates or updates the installation-wide sharing configuration.")
    @PutMapping
    public ResponseEntity<ApiResponse<Void>> upsert(@Valid @RequestBody SuperAdminShareSettingSaveRequest request) {
        superAdminShareSettingUseCase.upsert(request);
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Check whether shares exist across tenants",
            description = "Reports whether shared resources exist in the installation.")
    @GetMapping("/shares/exists")
    public ResponseEntity<ApiResponse<ShareExistsResponse>> getShareExists() {
        var response = superAdminShareSettingUseCase.existsShare();
        return ApiResponse.success(response);
    }
}
