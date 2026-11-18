package com.thinkfree.drive.api.v1.admin.smtpconfig.controller;

import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.smtpconfig.request.SmtpConfigRequest;
import com.thinkfree.drive.smtpconfig.dto.SmtpConfigSaveDto;
import com.thinkfree.drive.api.v1.admin.smtpconfig.request.SmtpConfigTestRequest;
import com.thinkfree.drive.api.v1.admin.smtpconfig.response.SmtpConfigResponse;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.smtpconfig.service.SmtpConfigService;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantFeatureRequired;
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
@RequestMapping("/api/v1/admins/smtp-config")
@Tag(name = "Admin - Mail")
public class SmtpConfigController {
    private final SmtpConfigService smtpConfigService;

    @ExternalApi
    @Operation(summary = "Save tenant mail settings",
            description = "Creates or updates the SMTP configuration used to send email for the tenant.")
    @PostMapping
    @TenantFeatureRequired(TenantFeature.MAIL)
    public ResponseEntity<ApiResponse<Void>> save(@AuthenticationPrincipal CustomUserDetails userDetails, @Valid @RequestBody SmtpConfigRequest request) {
        smtpConfigService.saveOrUpdateSmtpConfig(userDetails.getUserSeq(), toSaveDto(request));
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Test tenant mail settings",
            description = "Tests the saved SMTP configuration identified by smtpConfigSeq.")
    @PostMapping("/test")
    @TenantFeatureRequired(TenantFeature.MAIL)
    public ResponseEntity<ApiResponse<Void>> testSmtpConfig(@Valid @RequestBody SmtpConfigTestRequest request) {
        smtpConfigService.testSmtpConfig(request.getSmtpConfigSeq());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Get tenant mail settings",
            description = "Returns the current SMTP configuration and whether a password is set. The response does not contain the password.")
    @TenantRestriction(TenantFeature.SMTP_INFO_VIEW)
    @GetMapping
    public ResponseEntity<ApiResponse<SmtpConfigResponse>> find() {
        var result = smtpConfigService.getSmtpConfig();
        SmtpConfigResponse response = SmtpConfigResponse.of(result.smtpConfig(), result.hasPassword(), result.hasOauthInfo());
        return ApiResponse.success(response);
    }

    /** 웹 요청 → SMTP 설정 등록·수정 service 입력 */
    private static SmtpConfigSaveDto toSaveDto(SmtpConfigRequest request) {
        return new SmtpConfigSaveDto(request.getSmtpConfigSeq(), request.getClientName(), request.getHost(), request.getPort(),
                request.getEncryptionType(), request.getAuthType(), request.getUsername(), request.getEncPwd(), request.getOauthInfo(),
                request.getFromAddress(), request.getTestReceiverAddress());
    }
}

