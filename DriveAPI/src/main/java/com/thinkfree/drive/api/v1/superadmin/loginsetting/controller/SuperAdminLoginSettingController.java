package com.thinkfree.drive.api.v1.superadmin.loginsetting.controller;

import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.superadmin.loginsetting.request.SuperAdminLoginSettingRequest;
import com.thinkfree.drive.api.v1.superadmin.loginsetting.request.SuperAdminLoginSettingSaveRequest;
import com.thinkfree.drive.api.v1.superadmin.loginsetting.response.SuperAdminLoginSettingResponse;
import com.thinkfree.drive.api.v1.superadmin.loginsetting.usecase.SuperAdminLoginSettingUseCase;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Super admin - Sign-in settings")
@RestController
@RequestMapping("/api/v1/super-admin/login-settings")
@RequiredArgsConstructor
public class SuperAdminLoginSettingController {

    private final SuperAdminLoginSettingUseCase superAdminLoginSettingUseCase;

    @ExternalApi
    @Operation(summary = "Get installation sign-in settings",
            description = "Returns the installation-wide sign-in configuration.")
    @GetMapping
    public ResponseEntity<ApiResponse<SuperAdminLoginSettingResponse>> find(@ModelAttribute SuperAdminLoginSettingRequest request) {
        var response = superAdminLoginSettingUseCase.get(request);
        return ApiResponse.success(response);
    }

    @ExternalApi
    @Operation(summary = "Save installation sign-in settings",
            description = "Creates or updates the installation-wide sign-in configuration.")
    @PutMapping
    public ResponseEntity<ApiResponse<Void>> upsert(@Valid @RequestBody SuperAdminLoginSettingSaveRequest request) {
        superAdminLoginSettingUseCase.upsert(request);
        return ApiResponse.success();
    }
}
