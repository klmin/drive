package com.thinkfree.drive.api.v1.superadmin.tenant.controller;

import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.systemconfig.request.OfficeUpdateRequest;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.systemconfig.service.SystemConfigService;
import com.thinkfree.drive.tenant.service.TenantFindService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/super-admin/tenants/{tenantSeq}/office")
@Tag(name = "Super admin - Tenants")
public class SuperAdminOfficeController {
    private final TenantFindService tenants;
    private final SystemConfigService configs;

    /** Service-owned Office credentials are never returned by tenant admin APIs. */
    @ExternalApi
    @Operation(summary = "Save Office settings for a tenant",
            description = "Validates and updates the Office integration settings for the selected tenant.")
    @PutMapping
    public ResponseEntity<ApiResponse<Void>> update(@PathVariable Integer tenantSeq,
            @AuthenticationPrincipal CustomUserDetails actor, @Valid @RequestBody OfficeUpdateRequest request) {
        tenants.findByTenantSeq(tenantSeq);
        configs.upsertManagedOffice(tenantSeq, actor.getUserSeq(), request);
        return ApiResponse.success();
    }
}
