package com.thinkfree.drive.api.v1.admin.apikey.controller;

import com.thinkfree.drive.global.annotation.ParameterDoc;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.apikey.request.AdminApiKeyCreateRequest;
import com.thinkfree.drive.api.v1.admin.apikey.request.AdminApiKeyExpireDateUpdateRequest;
import com.thinkfree.drive.api.v1.admin.apikey.response.ApiKeyCreateResponse;
import com.thinkfree.drive.api.v1.admin.apikey.response.ApiKeyListResponse;
import com.thinkfree.drive.api.v1.admin.apikey.response.ApiKeyReissueResponse;
import com.thinkfree.drive.api.v1.admin.apikey.converter.AdminApiKeyRequestConverter;
import com.thinkfree.drive.api.v1.admin.apikey.converter.AdminApiKeyResponseConverter;
import com.thinkfree.drive.apikey.exception.ApiKeyErrorCode;
import com.thinkfree.drive.application.apikey.ApiKeyActor;
import com.thinkfree.drive.application.apikey.ApiKeyScope;
import com.thinkfree.drive.application.apikey.ApiKeyUseCase;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.valid.DriveAssert;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.global.security.tenant.annotation.TenantApiKey;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantRestriction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - API keys")
@RestController
@RequestMapping("/api/v1/admins/api-keys")
@RequiredArgsConstructor
public class AdminApiKeyController {

    private final AdminApiKeyResponseConverter adminApiKeyResponseConverter;
    private final ApiKeyUseCase apiKeyUseCase;
    private final AdminApiKeyRequestConverter adminApiKeyRequestConverter;

    @ExternalApi
    @Operation(summary = "List tenant API keys",
            description = "Returns a page of API key records for the tenant. Set mine to true to limit the results to keys you issued.")
    @TenantRestriction(TenantFeature.API_KEY_INFO_VIEW)
    @GetMapping
    public ResponseEntity<ApiResponse<List<ApiKeyListResponse>>> findAllPaginated(@ModelAttribute @Valid CursorPagingRequest pagingRequest,
                                                                                  @ParameterDoc(title = "Only my API Keys", description = "Set true to list only API Keys belonging to the current administrator.") @RequestParam(required = false) Boolean mine,
                                                                                  @AuthenticationPrincipal CustomUserDetails loginUser) {
        var registerUserSeq = Boolean.TRUE.equals(mine) ? loginUser.getUserSeq() : null;
        var command = adminApiKeyRequestConverter.toListCommand(pagingRequest, registerUserSeq);
        var pageInfo = apiKeyUseCase.findAllPaginated(command, ApiKeyScope.tenant(TenantContext.getOrThrow()));
        return ApiResponse.successWithCursor(adminApiKeyResponseConverter.toListResponse(pageInfo.list()),
                adminApiKeyResponseConverter.toCursorApiPage(pageInfo));
    }

    /**
     * 대상 계정 미지정 시 본인 키. 지정 시 자기 테넌트 계정이어야 함.
     * 슈퍼관리자는 진입 키로 관리자 화면에 들어와도 이 경로로는 발급할 수 없다.
     */
    @ExternalApi
    @TenantRestriction(TenantFeature.API_KEY_MANAGE)
    @Operation(summary = "Create a tenant API key",
            description = "Issues a key for the account in userSeq, or for yourself when omitted. The new key value is shown only in this response.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<ApiKeyCreateResponse>> create(@Valid @RequestBody AdminApiKeyCreateRequest request,
                                                                    @AuthenticationPrincipal CustomUserDetails loginUser) {
        var actor = ApiKeyActor.of(loginUser);

        // 경로별 규칙이라 세 경로 공용인 usecase 가 아니라 어댑터에 둔다
        DriveAssert.throwIfTrue(RoleType.SUPER_ADMIN == actor.roleType(),
                ApiKeyErrorCode.API_KEY_SUPER_ADMIN_PATH_REQUIRED);

        var command = adminApiKeyRequestConverter.toIssueCommand(request);
        var rawApiKey = apiKeyUseCase.issue(command, actor);
        return ApiResponse.created(new ApiKeyCreateResponse(rawApiKey));
    }

    @ExternalApi
    @TenantRestriction(TenantFeature.API_KEY_MANAGE)
    @Operation(summary = "Change a tenant API key expiration",
            description = "Updates the selected key's expiration date. Extending the date can make an expired active key usable again.")
    @PatchMapping("/{apiKeySeq}/expire-date")
    public ResponseEntity<ApiResponse<Void>> updateExpireDate(@TenantApiKey @ParameterDoc(title = "API Key record ID", description = "Numeric apiKeySeq returned by the API Key list. Do not enter the secret API Key value.") @PathVariable Integer apiKeySeq,
                                                              @Valid @RequestBody AdminApiKeyExpireDateUpdateRequest request) {
        var command = adminApiKeyRequestConverter.toExpireDateUpdateCommand(apiKeySeq, request);
        apiKeyUseCase.updateExpireDate(command, ApiKeyScope.tenant(TenantContext.getOrThrow()));
        return ApiResponse.success();
    }

    @ExternalApi
    @TenantRestriction(TenantFeature.API_KEY_MANAGE)
    @Operation(summary = "Activate a tenant API key",
            description = "Enables the selected API key in the current tenant. The key remains subject to its expiration date.")
    @PatchMapping("/{apiKeySeq}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@TenantApiKey @ParameterDoc(title = "API Key record ID", description = "Numeric apiKeySeq returned by the API Key list. Do not enter the secret API Key value.") @PathVariable Integer apiKeySeq) {
        apiKeyUseCase.activate(apiKeySeq, ApiKeyScope.tenant(TenantContext.getOrThrow()));
        return ApiResponse.success();
    }

    @ExternalApi
    @TenantRestriction(TenantFeature.API_KEY_MANAGE)
    @Operation(summary = "Deactivate a tenant API key",
            description = "Disables authentication with the selected API key without removing its record.")
    @PatchMapping("/{apiKeySeq}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@TenantApiKey @ParameterDoc(title = "API Key record ID", description = "Numeric apiKeySeq returned by the API Key list. Do not enter the secret API Key value.") @PathVariable Integer apiKeySeq) {
        apiKeyUseCase.deactivate(apiKeySeq, ApiKeyScope.tenant(TenantContext.getOrThrow()));
        return ApiResponse.success();
    }

    @ExternalApi
    @TenantRestriction(TenantFeature.API_KEY_MANAGE)
    @Operation(summary = "Reissue a tenant API key",
            description = "Replaces the selected key value. Copy the new value from this response because it is only shown at issuance.")
    @PostMapping("/{apiKeySeq}/reissue")
    public ResponseEntity<ApiResponse<ApiKeyReissueResponse>> reissue(@TenantApiKey @ParameterDoc(title = "API Key record ID", description = "Numeric apiKeySeq returned by the API Key list. Do not enter the secret API Key value.") @PathVariable Integer apiKeySeq) {
        var rawApiKey = apiKeyUseCase.reissue(apiKeySeq, ApiKeyScope.tenant(TenantContext.getOrThrow()));
        return ApiResponse.success(new ApiKeyReissueResponse(rawApiKey));
    }
}
