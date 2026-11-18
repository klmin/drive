package com.thinkfree.drive.api.v1.auth.mfa.controller;

import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.auth.mfa.request.MfaConfirmRequest;
import com.thinkfree.drive.api.v1.auth.mfa.request.MfaSetupRequest;
import com.thinkfree.drive.api.v1.auth.mfa.response.MfaSetupResponse;
import com.thinkfree.drive.api.v1.auth.mfa.request.MfaVerifyRequest;
import com.thinkfree.drive.api.v1.auth.mfa.usecase.AuthMfaUseCase;
import com.thinkfree.drive.api.v1.auth.response.LoginResponse;
import com.thinkfree.drive.auth.cookie.AuthCookieManager;
import com.thinkfree.drive.user.enums.RoleType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/mfa")
public class AuthMfaController {

    private final AuthMfaUseCase authMfaUseCase;
    private final AuthCookieManager authCookieManager;

    @Operation(summary = "Set up MFA during sign-in",
            description = "Issues an MFA QR code during sign-in when the tenant requires MFA.")
    @PostMapping("/setup")
    public ResponseEntity<ApiResponse<MfaSetupResponse>> setup(@Valid @RequestBody MfaSetupRequest request) {
        var response = authMfaUseCase.setup(request);
        return ApiResponse.success(response);
    }

    @Operation(summary = "Confirm MFA setup during sign-in",
            description = "Verifies the OTP code from the new MFA setup and completes sign-in.")
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<LoginResponse>> confirm(@Valid @RequestBody MfaConfirmRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        var response = authMfaUseCase.confirm(request, httpRequest);
        authCookieManager.issue(httpRequest, httpResponse, RoleType.USER, response.getAccessToken(), response.getRefreshToken());
        return ApiResponse.success(response);
    }

    @Operation(summary = "Verify MFA",
            description = "Verifies the OTP code as the second sign-in step and completes sign-in.")
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<LoginResponse>> verify(@Valid @RequestBody MfaVerifyRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        var response = authMfaUseCase.verify(request.t(), request.c(), httpRequest);
        authCookieManager.issue(httpRequest, httpResponse, RoleType.USER, response.getAccessToken(), response.getRefreshToken());
        return ApiResponse.success(response);
    }
}
