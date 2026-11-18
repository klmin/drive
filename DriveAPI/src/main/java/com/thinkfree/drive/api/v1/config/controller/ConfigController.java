package com.thinkfree.drive.api.v1.config.controller;

import com.hancom.weboffice.common.license.exception.LicenseException;
import com.hancom.weboffice.common.license.exception.LicenseValidatorException;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.config.response.ClientConfigResponse;
import com.thinkfree.drive.api.v1.config.response.FileSizeLimitResponse;
import com.thinkfree.drive.api.v1.config.response.OfficeResponse;
import com.thinkfree.drive.api.v1.config.response.TenantConfigResponse;
import com.thinkfree.drive.api.v1.config.usecase.ConfigUseCase;
import com.thinkfree.drive.global.annotation.AnonymousAccessible;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.license.dto.LicenseErrorResponse;
import com.thinkfree.drive.license.exception.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/config")
@Slf4j
@Tag(name = "Client configuration")
public class ConfigController {

    private final ConfigUseCase configUseCase;

    @ExternalApi
    @Operation(summary = "Get the file upload limit",
            description = "Returns the maximum individual file size reported to Drive clients for this tenant.")
    @GetMapping("/file-size-limit")
    public ResponseEntity<ApiResponse<FileSizeLimitResponse>> getFileSizeLimit() {
        return ApiResponse.success(configUseCase.getFileSizeLimit());
    }
    
    @ExternalApi
    @Operation(summary = "Get Office client settings",
            description = "Returns the Office connection type and authentication mode used by Drive clients.")
    @AnonymousAccessible
    @GetMapping("/office")
    public ResponseEntity<ApiResponse<OfficeResponse>> getOfficeConfig() {
        OfficeResponse response = configUseCase.getOfficeConfig();
        return ApiResponse.success(response);
    }

    @ExternalApi
    @Operation(summary = "Get Drive client settings",
            description = "Returns the current tenant settings used to initialize the Drive client.")
    @AnonymousAccessible
    @GetMapping
    public ResponseEntity<ApiResponse<ClientConfigResponse>> getClientConfig() {
        var response = configUseCase.getClientConfig();
        return ApiResponse.success(response);
    }

    /** 화면 전용(외부 API 아님) — 외부 API 호출자(프로비저닝 서버)는 생성 시점에 유형·모드를 이미 안다 */
    @Operation(summary = "Get tenant type and user mode",
            description = "Returns the requesting tenant's tenantType and userMode. Both are fixed after creation.")
    @AnonymousAccessible
    @GetMapping("/tenant")
    public ResponseEntity<ApiResponse<TenantConfigResponse>> getTenantConfig() {
        return ApiResponse.success(configUseCase.getTenantConfig());
    }

    /** USER·ADMIN 공용 — 응답은 ApiResponse 로 감싸지 않는다(프론트 차단 판단이 이 형태를 읽음) */
    @ExternalApi
    @Operation(summary = "Get the license status",
            description = "Returns the license state for the current tenant.")
    @GetMapping("/license-status")
    public ResponseEntity<?> getLicenseStatus() {
        try {
            Map<String, Object> licenseStateMap = configUseCase.getLicenseStatus();
            if (licenseStateMap == null) {
                ResultCode resultCode = ResultCode.LICENSE_FILE_NOT_EXIST;

                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(new LicenseErrorResponse(resultCode.getCode(), resultCode.name()));
            }

            return ResponseEntity.ok(licenseStateMap);

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
