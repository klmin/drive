package com.thinkfree.drive.api.v1.superadmin.apikey.controller;

import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.superadmin.apikey.request.SuperAdminApiKeyCreateRequest;
import com.thinkfree.drive.api.v1.superadmin.apikey.request.SuperAdminApiKeyExpireDateUpdateRequest;
import com.thinkfree.drive.api.v1.superadmin.apikey.request.SuperAdminApiKeyProtectionUpdateRequest;
import com.thinkfree.drive.api.v1.superadmin.apikey.response.ApiKeyCreateResponse;
import com.thinkfree.drive.api.v1.superadmin.apikey.response.ApiKeyListResponse;
import com.thinkfree.drive.api.v1.superadmin.apikey.response.ApiKeyReissueResponse;
import com.thinkfree.drive.api.v1.superadmin.apikey.converter.SuperAdminApiKeyRequestConverter;
import com.thinkfree.drive.api.v1.superadmin.apikey.converter.SuperAdminApiKeyResponseConverter;
import com.thinkfree.drive.application.apikey.ApiKeyActor;
import com.thinkfree.drive.application.apikey.ApiKeyScope;
import com.thinkfree.drive.application.apikey.ApiKeyUseCase;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Super admin - API keys")
@RestController
@RequestMapping("/api/v1/super-admin/api-keys")
@RequiredArgsConstructor
public class SuperAdminApiKeyController {

    private final SuperAdminApiKeyResponseConverter superAdminApiKeyResponseConverter;
    private final ApiKeyUseCase apiKeyUseCase;
    private final SuperAdminApiKeyRequestConverter superAdminApiKeyRequestConverter;

    @ExternalApi
    @Operation(summary = "Issue an API key for a tenant account",
            description = "Creates a key for the selected account in the specified tenant and returns the new key value.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<ApiKeyCreateResponse>> create(@Valid @RequestBody SuperAdminApiKeyCreateRequest request,
                                                                    @AuthenticationPrincipal CustomUserDetails loginUser) {
        var command = superAdminApiKeyRequestConverter.toIssueCommand(request);
        var rawApiKey = apiKeyUseCase.issue(command, ApiKeyActor.of(loginUser));
        return ApiResponse.created(new ApiKeyCreateResponse(rawApiKey));
    }

    @ExternalApi
    @Operation(summary = "List installation API keys",
            description = "Returns a page of API keys across tenants. Use ts to select a tenant and mine to filter by issuer.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ApiKeyListResponse>>> findAllPaginated(@ModelAttribute @Valid CursorPagingRequest pagingRequest,
                                                                                  @RequestParam(required = false) Integer ts,
                                                                                  @RequestParam(required = false) Boolean mine,
                                                                                  @AuthenticationPrincipal CustomUserDetails loginUser) {
        var registerUserSeq = Boolean.TRUE.equals(mine) ? loginUser.getUserSeq() : null;
        var command = superAdminApiKeyRequestConverter.toListCommand(pagingRequest, ts, registerUserSeq);
        var pageInfo = apiKeyUseCase.findAllPaginated(command, ApiKeyScope.all());
        return ApiResponse.successWithCursor(superAdminApiKeyResponseConverter.toListResponse(pageInfo.list()),
                superAdminApiKeyResponseConverter.toCursorApiPage(pageInfo));
    }

    @ExternalApi
    @Operation(summary = "Change an installation API key expiration",
            description = "Updates the selected key's expiration date.")
    @PatchMapping("/{apiKeySeq}/expire-date")
    public ResponseEntity<ApiResponse<Void>> updateExpireDate(@PathVariable Integer apiKeySeq,
                                                              @Valid @RequestBody SuperAdminApiKeyExpireDateUpdateRequest request) {
        var command = superAdminApiKeyRequestConverter.toExpireDateUpdateCommand(apiKeySeq, request);
        apiKeyUseCase.updateExpireDate(command, ApiKeyScope.all());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Activate an installation API key",
            description = "Enables the selected key, subject to its expiration date.")
    @PatchMapping("/{apiKeySeq}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Integer apiKeySeq) {
        apiKeyUseCase.activate(apiKeySeq, ApiKeyScope.all());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Deactivate an installation API key",
            description = "Disables authentication with the selected key without removing its record.")
    @PatchMapping("/{apiKeySeq}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Integer apiKeySeq) {
        apiKeyUseCase.deactivate(apiKeySeq, ApiKeyScope.all());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Change API key protection",
            description = "Set pt to true to block tenant-level changes to the key, or false to allow them.")
    @PatchMapping("/{apiKeySeq}/protection")
    public ResponseEntity<ApiResponse<Void>> updateProtection(@PathVariable Integer apiKeySeq,
                                                              @Valid @RequestBody SuperAdminApiKeyProtectionUpdateRequest request) {
        apiKeyUseCase.updateProtectionForSuperAdmin(apiKeySeq, request.pt());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Reissue an installation API key",
            description = "Replaces the selected key value and returns the new value in the response.")
    @PostMapping("/{apiKeySeq}/reissue")
    public ResponseEntity<ApiResponse<ApiKeyReissueResponse>> reissue(@PathVariable Integer apiKeySeq) {
        var rawApiKey = apiKeyUseCase.reissue(apiKeySeq, ApiKeyScope.all());
        return ApiResponse.success(new ApiKeyReissueResponse(rawApiKey));
    }
}
