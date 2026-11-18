package com.thinkfree.drive.api.v1.admin.loginsetting.controller;

import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.loginsetting.request.LoginSettingRequest;
import com.thinkfree.drive.api.v1.admin.loginsetting.request.LoginSettingSaveRequest;
import com.thinkfree.drive.api.v1.admin.loginsetting.response.LoginSettingResponse;
import com.thinkfree.drive.api.v1.admin.loginsetting.usecase.AdminLoginSettingUseCase;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantRestriction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Sign-in settings")
@RestController
@RequestMapping("/api/v1/admins/login-settings")
@RequiredArgsConstructor
public class AdminLoginSettingController {

    private final AdminLoginSettingUseCase adminLoginSettingUseCase;

    @ExternalApi
    @Operation(summary = "Get tenant sign-in settings",
            description = "Returns the sign-in configuration for the current tenant using the options in the request.")
    @GetMapping
    public ResponseEntity<ApiResponse<LoginSettingResponse>> find(@ModelAttribute LoginSettingRequest request) {
        var response = adminLoginSettingUseCase.get(request);
        return ApiResponse.success(response);
    }

    @ExternalApi
    @TenantRestriction(TenantFeature.LOGIN_SETTING)
    @Operation(summary = "Update tenant sign-in settings",
            description = "Saves the supplied sign-in configuration for the current tenant.")
    @PatchMapping
    public ResponseEntity<ApiResponse<Void>> save(@Valid @RequestBody LoginSettingSaveRequest request) {
        adminLoginSettingUseCase.save(request);
        return ApiResponse.success();
    }
}
