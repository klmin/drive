package com.thinkfree.drive.api.v1.superadmin.basedomain.controller;

import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.superadmin.basedomain.request.SuperAdminBaseDomainUpsertRequest;
import com.thinkfree.drive.api.v1.superadmin.basedomain.response.SuperAdminBaseDomainResponse;
import com.thinkfree.drive.api.v1.superadmin.basedomain.usecase.SuperAdminBaseDomainUseCase;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Super admin - Base domain")
@RestController
@RequestMapping("/api/v1/super-admin/base-domain")
@RequiredArgsConstructor
public class SuperAdminBaseDomainController {

    private final SuperAdminBaseDomainUseCase superAdminBaseDomainUseCase;

    @ExternalApi
    @Operation(summary = "Get the installation base domain",
            description = "Returns the base domain configuration used to construct tenant addresses.")
    @GetMapping
    public ResponseEntity<ApiResponse<SuperAdminBaseDomainResponse>> get() {
        var response = superAdminBaseDomainUseCase.get();
        return ApiResponse.success(response);
    }

    @ExternalApi
    @Operation(summary = "Save the installation base domain",
            description = "Saves the base domain and recalculates the domains of all connected tenants.")
    @PutMapping
    public ResponseEntity<ApiResponse<Void>> upsert(@Valid @RequestBody SuperAdminBaseDomainUpsertRequest request) {
        superAdminBaseDomainUseCase.upsert(request);
        return ApiResponse.success();
    }
}
