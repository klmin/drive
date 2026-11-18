package com.thinkfree.drive.api.v1.admin.accessrestriction.controller;

import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.accessrestriction.request.AllowedIpsUpdateRequest;
import com.thinkfree.drive.api.v1.admin.accessrestriction.usecase.AccessAllowedIpUseCase;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantRestriction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admins/access-restriction")
@Tag(name = "Admin - Access restriction")
public class AccessAllowedIpController {

    private final AccessAllowedIpUseCase accessAllowedIpUseCase;

    @ExternalApi
    @TenantRestriction(TenantFeature.ACCESS_RESTRICTION)
    @Operation(summary = "Set allowed IP addresses",
            description = "Updates the IP allowlist used by the tenant when IP-based access restrictions are enabled.")
    @PutMapping("/ips")
    public ResponseEntity<ApiResponse<Void>> putAllowedIps(@AuthenticationPrincipal CustomUserDetails userDetails, @Valid @RequestBody AllowedIpsUpdateRequest request) {
        accessAllowedIpUseCase.putAllowedIps(request, userDetails.getUserSeq());
        return ApiResponse.success();
    }
}
