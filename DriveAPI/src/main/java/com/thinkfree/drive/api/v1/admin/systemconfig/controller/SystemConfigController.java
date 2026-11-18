package com.thinkfree.drive.api.v1.admin.systemconfig.controller;

import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.systemconfig.request.*;
import com.thinkfree.drive.api.v1.admin.systemconfig.response.*;
import com.thinkfree.drive.api.v1.admin.systemconfig.usecase.AdminSystemConfigUseCase;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantRestriction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admins/system-config")
@Tag(name = "Admin - System settings")
public class SystemConfigController {
    
    private final AdminSystemConfigUseCase adminSystemConfigUseCase;
    
    @ExternalApi
    @Operation(summary = "Get general tenant settings",
            description = "Returns the current tenant's general configuration.")
    @TenantRestriction(TenantFeature.SYSTEM_INFO_VIEW)
    @GetMapping("/general")
    public ResponseEntity<ApiResponse<GeneralResponse>> getGeneralConfig() {
        GeneralResponse response = adminSystemConfigUseCase.getGeneralConfig();
        return ApiResponse.success(response);
    }
    @ExternalApi
    @TenantRestriction(TenantFeature.SYSTEM_CONFIG)
    @Operation(summary = "Save general tenant settings",
            description = "Creates or updates the current tenant's general configuration from the supplied values.")
    @PutMapping("/general")
    public ResponseEntity<ApiResponse<Void>> putGeneral(@Valid @RequestBody GeneralUpdateRequest request) {
        adminSystemConfigUseCase.putGeneral(request);
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Get Office integration settings",
            description = "Returns the tenant's Office integration configuration. Drive masks managed connection values for system-managed single-account tenants.")
    @TenantRestriction(TenantFeature.OFFICE_INFO_VIEW)
    @GetMapping("/office")
    public ResponseEntity<ApiResponse<OfficeResponse>> getOfficeConfig() {
        OfficeResponse response = adminSystemConfigUseCase.getOfficeConfig();
        var result = ApiResponse.success(response);
        return ResponseEntity.status(result.getStatusCode()).headers(result.getHeaders())
                .cacheControl(org.springframework.http.CacheControl.noStore()).body(result.getBody());
    }
    
    @ExternalApi
    @TenantRestriction(TenantFeature.SYSTEM_CONFIG)
    @Operation(summary = "Save Office integration settings",
            description = "Validates and saves the tenant's selected Office integration and its connection settings.")
    @PutMapping("/office")
    public ResponseEntity<ApiResponse<Void>> putOffice(@AuthenticationPrincipal CustomUserDetails userDetails, @Valid @RequestBody OfficeUpdateRequest request) {
        adminSystemConfigUseCase.putOffice(request, userDetails.getUserSeq());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Get storage configuration",
            description = "Returns information about the storage configured for this Drive installation.")
    @TenantRestriction(TenantFeature.STORAGE_INFO_VIEW)
    @GetMapping("/storage")
    public ResponseEntity<ApiResponse<StorageResponse>> getStorageInfo() {
        StorageResponse response = adminSystemConfigUseCase.getStorageInfo();
        return ApiResponse.success(response);
    }

    @ExternalApi
    @Operation(summary = "Get tenant storage usage",
            description = "Returns the tenant's total quota and the sum of quotas allocated to its users.")
    @GetMapping("/storage-quota")
    public ResponseEntity<ApiResponse<StorageQuotaResponse>> getStorageQuota() {
        StorageQuotaResponse response = adminSystemConfigUseCase.getStorageQuota();
        return ApiResponse.success(response);
    }
    
    @ExternalApi
    @Operation(summary = "Get Intellect settings",
            description = "Returns the current tenant's configuration for the Intellect integration.")
    @GetMapping("/intellect")
    public ResponseEntity<ApiResponse<IntellectResponse>> getIntellectInfo() {
        IntellectResponse response = adminSystemConfigUseCase.getIntellectInfo();
        return ApiResponse.success(response);
    }
    
    @ExternalApi
    @TenantRestriction(TenantFeature.SYSTEM_CONFIG)
    @Operation(summary = "Update Intellect settings",
            description = "Saves the Intellect integration settings for the current tenant.")
    @PutMapping("/intellect")
    public ResponseEntity<ApiResponse<Void>> putIntellect(@AuthenticationPrincipal CustomUserDetails userDetails, @Valid @RequestBody IntellectUpdateRequest request) {
        adminSystemConfigUseCase.putIntellect(request, userDetails.getUserSeq());
        return ApiResponse.success();
    }
    
    @ExternalApi
    @Operation(summary = "Get multifactor authentication settings",
            description = "Returns whether the tenant requires multifactor authentication.")
    @GetMapping("/mfa-status")
    public ResponseEntity<ApiResponse<MfaStatusResponse>> getMfaStatus() {
        return ApiResponse.success(adminSystemConfigUseCase.getMfaStatus());
    }

    @ExternalApi
    @TenantRestriction(TenantFeature.MFA_SETTING)
    @Operation(summary = "Update multifactor authentication settings",
            description = "Changes the tenant's multifactor authentication requirement.")
    @PutMapping("/mfa-status")
    public ResponseEntity<ApiResponse<Void>> putMfaStatus(@AuthenticationPrincipal CustomUserDetails userDetails, @Valid @RequestBody MfaStatusUpdateRequest request) {
        adminSystemConfigUseCase.putMfaStatus(request, userDetails.getUserSeq());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Get tenant file size limit",
            description = "Returns the configured maximum size for an individual file upload in the current tenant.")
    @GetMapping("/file-size-limit")
    public ResponseEntity<ApiResponse<FileSizeLimitResponse>> getFileSizeLimit() {
        FileSizeLimitResponse response = adminSystemConfigUseCase.getFileSizeLimit();
        return ApiResponse.success(response);
    }

    @ExternalApi
    @Operation(summary = "Set tenant file size limit",
            description = "Updates the maximum size allowed for an individual file upload in the current tenant.")
    @PutMapping("/file-size-limit")
    public ResponseEntity<ApiResponse<Void>> putFileSizeLimit(@AuthenticationPrincipal CustomUserDetails userDetails, @Valid @RequestBody FileSizeLimitUpdateRequest request) {
        adminSystemConfigUseCase.putFileSizeLimit(request, userDetails.getUserSeq());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Get access restrictions",
            description = "Returns the current access restriction settings for the tenant.")
    @GetMapping("/access-restriction-status")
    public ResponseEntity<ApiResponse<AccessRestrictionResponse>> getAccessRestrictionStatus() {
        return ApiResponse.success(adminSystemConfigUseCase.getAccessRestrictionStatus());
    }

    @ExternalApi
    @TenantRestriction(TenantFeature.ACCESS_RESTRICTION)
    @Operation(summary = "Update access restrictions",
            description = "Changes which access restrictions are enabled for the tenant.")
    @PutMapping("/access-restriction-status")
    public ResponseEntity<ApiResponse<Void>> putAccessRestrictionStatus(@AuthenticationPrincipal CustomUserDetails userDetails, @Valid @RequestBody AccessRestrictionUpdateRequest request) {
        adminSystemConfigUseCase.putAccessRestrictionStatus(request, userDetails.getUserSeq());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Check for existing shares",
            description = "Reports whether the current tenant has shared resources.")
    @GetMapping("/shares/exists")
    @TenantRestriction(TenantFeature.SHARE)
    public ResponseEntity<ApiResponse<ShareExistsResponse>> getShareExists() {
        var response = adminSystemConfigUseCase.existsShare();
        return ApiResponse.success(response);
    }

}
