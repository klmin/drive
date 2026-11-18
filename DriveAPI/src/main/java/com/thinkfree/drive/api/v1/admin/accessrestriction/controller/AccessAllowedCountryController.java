package com.thinkfree.drive.api.v1.admin.accessrestriction.controller;

import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.accessrestriction.request.AllowedCountriesUpdateRequest;
import com.thinkfree.drive.api.v1.admin.accessrestriction.usecase.AccessAllowedCountryUseCase;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admins/access-restriction")
@Tag(name = "Admin - Access restriction")
public class AccessAllowedCountryController {

    private final AccessAllowedCountryUseCase accessAllowedCountryUseCase;

    @ExternalApi
    @TenantRestriction(TenantFeature.ACCESS_RESTRICTION)
    @Operation(summary = "Set allowed countries",
            description = "Updates the countries from which users may access the tenant when country restrictions are enabled.")
    @PutMapping("/countries")
    public ResponseEntity<ApiResponse<Void>> putAllowedCountries(@AuthenticationPrincipal CustomUserDetails userDetails, @Valid @RequestBody AllowedCountriesUpdateRequest request) {
        accessAllowedCountryUseCase.putAllowedCountries(request, userDetails.getUserSeq());
        return ApiResponse.success();
    }

}
