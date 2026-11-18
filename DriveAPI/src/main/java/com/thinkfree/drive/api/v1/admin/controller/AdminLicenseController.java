package com.thinkfree.drive.api.v1.admin.controller;

import com.thinkfree.drive.global.annotation.ParameterDoc;
import com.hancom.weboffice.common.license.exception.LicenseException;
import com.hancom.weboffice.common.license.exception.LicenseValidatorException;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantFeatureRequired;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.response.LicenseAuthResultResponse;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.license.auth.result.LicenseAuthResult;
import com.thinkfree.drive.license.auth.service.LicenseAuthService;
import com.thinkfree.drive.license.dto.LicenseErrorResponse;
import com.thinkfree.drive.license.exception.ResultCode;
import com.thinkfree.drive.license.service.LicenseService;
import com.thinkfree.drive.license.service.SeatLicenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admins/license")
@Tag(name = "Admin - License")
public class AdminLicenseController {
    private static final DateTimeFormatter AUTH_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LicenseService licenseService;
    private final SeatLicenseService seatLicenseService;
    private final LicenseAuthService licenseAuthService;

    @ExternalApi
    @Operation(summary = "Register a tenant license",
            description = "Uploads the license file for the current tenant. The tenant must allow license management.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @TenantFeatureRequired(TenantFeature.LICENSE_MANAGE)
    public ResponseEntity<ApiResponse<Void>> registerLicense(@ParameterDoc(title = "License file", description = "Choose the Drive license file to upload.") @RequestParam(value = "licenseFile") MultipartFile licenseFile) {
        byte[] licenseBlob;
        try {
            licenseBlob = licenseFile.getBytes();
        } catch (IOException e) {
            throw new ApiRuntimeException(ResultCode.LICENSE);
        }

        licenseService.registerLicense(TenantContext.getOrThrow(), licenseBlob);
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Verify the tenant license online",
            description = "Requests online authentication for the current license and returns its status, last successful authentication time, and error code when available.")
    @PostMapping("/auth")
    @TenantFeatureRequired(TenantFeature.LICENSE_MANAGE)
    public ResponseEntity<ApiResponse<LicenseAuthResultResponse>> authenticateLicense() {
        LicenseAuthResult authResult = licenseAuthService.authenticateManually(TenantContext.getOrThrow());
        return ApiResponse.success(new LicenseAuthResultResponse(
                authResult.status().name(),
                authResult.successAt() == null ? null : DriveTime.toLocal(authResult.successAt()).format(AUTH_TIME_FORMAT),
                authResult.errorCode()));
    }

    @ExternalApi
    @Operation(summary = "Get the tenant license",
            description = "Returns the registered license and current active seat usage. Returns 404 if the tenant has no registered license.")
    @GetMapping
    public ResponseEntity<?> getLicense() {
        int tenantSeq = TenantContext.getOrThrow();
        try {
            Map<String, Object> licenseMap = licenseService.getLicense(tenantSeq);

            if (licenseMap == null) {
                ResultCode resultCode = ResultCode.LICENSE_FILE_NOT_EXIST;

                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(new LicenseErrorResponse(resultCode.getCode(), resultCode.name()));
            }
            // 화면 표시용 라이선스 데이터 변환 및 추가
            int currentActiverUserNum = seatLicenseService.getCurrentActiveUserNum(tenantSeq);
            licenseMap = licenseService.convertLicenseForDisplay(licenseMap, currentActiverUserNum);

            return ResponseEntity.ok(licenseMap);

        } catch (LicenseValidatorException e) {
            ResultCode resultCode = ResultCode.fromCode(e.getResultCode());

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new LicenseErrorResponse(resultCode.getCode(), resultCode.name()));
        } catch (LicenseException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new LicenseErrorResponse(ResultCode.LICENSE));
        }
    }

}
