package com.thinkfree.drive.api.v1.user.apikey.controller;

import com.thinkfree.drive.global.annotation.ParameterDoc;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.user.apikey.request.UserApiKeyCreateRequest;
import com.thinkfree.drive.api.v1.user.apikey.request.UserApiKeyExpireDateUpdateRequest;
import com.thinkfree.drive.api.v1.user.apikey.response.ApiKeyCreateResponse;
import com.thinkfree.drive.api.v1.user.apikey.response.ApiKeyListResponse;
import com.thinkfree.drive.api.v1.user.apikey.response.ApiKeyReissueResponse;
import com.thinkfree.drive.api.v1.user.apikey.converter.UserApiKeyRequestConverter;
import com.thinkfree.drive.api.v1.user.apikey.converter.UserApiKeyResponseConverter;
import com.thinkfree.drive.application.apikey.ApiKeyActor;
import com.thinkfree.drive.application.apikey.ApiKeyScope;
import com.thinkfree.drive.application.apikey.ApiKeyUseCase;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantRestriction;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "API keys")
@RestController
@RequestMapping("/api/v1/users/api-keys")
@RequiredArgsConstructor
public class UserApiKeyController {

    private final UserApiKeyResponseConverter userApiKeyResponseConverter;
    private final ApiKeyUseCase apiKeyUseCase;
    private final UserApiKeyRequestConverter userApiKeyRequestConverter;

    @ExternalApi
    @TenantRestriction(TenantFeature.API_KEY_MANAGE)
    @Operation(summary = "Create your API key",
            description = "Issues a key that acts as the signed-in user and returns the new key value in this response.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<ApiKeyCreateResponse>> create(@Valid @RequestBody UserApiKeyCreateRequest request,
                                                                    @AuthenticationPrincipal CustomUserDetails loginUser) {
        var command = userApiKeyRequestConverter.toIssueCommand(request);
        var rawApiKey = apiKeyUseCase.issue(command, ApiKeyActor.of(loginUser));
        return ApiResponse.created(new ApiKeyCreateResponse(rawApiKey));
    }

    @ExternalApi
    @Operation(summary = "List your API keys",
            description = "Returns a page of API keys issued by the signed-in user.")
    @TenantRestriction(TenantFeature.API_KEY_INFO_VIEW)
    @GetMapping
    public ResponseEntity<ApiResponse<List<ApiKeyListResponse>>> findAllPaginated(@ModelAttribute @Valid CursorPagingRequest pagingRequest,
                                                                                  @AuthenticationPrincipal CustomUserDetails loginUser) {
        var command = userApiKeyRequestConverter.toListCommand(pagingRequest);
        var pageInfo = apiKeyUseCase.findAllPaginated(command, ApiKeyScope.registerUser(loginUser.getUserSeq()));
        return ApiResponse.successWithCursor(userApiKeyResponseConverter.toListResponse(pageInfo.list()),
                userApiKeyResponseConverter.toCursorApiPage(pageInfo));
    }

    /*
     * 대상 제한(본인 발급 키)은 UPDATE 조건으로 건다.
     * @TenantApiKey 는 TenantBoundaryAspect 의 pointcut 이 admin 패키지 한정이라 여기서는 동작하지 않고,
     * 애초에 테넌트 경계가 아니라 소유권 판정이라 그 어노테이션으로 표현되지도 않는다.
     */
    @ExternalApi
    @TenantRestriction(TenantFeature.API_KEY_MANAGE)
    @Operation(summary = "Change your API key expiration",
            description = "Updates the expiration of a key issued by the signed-in user. Extending the date can make an expired active key usable again.")
    @PatchMapping("/{apiKeySeq}/expire-date")
    public ResponseEntity<ApiResponse<Void>> updateExpireDate(@ParameterDoc(title = "API Key record ID", description = "Numeric apiKeySeq returned by the API Key list. Do not enter the secret API Key value.") @PathVariable Integer apiKeySeq,
                                                              @Valid @RequestBody UserApiKeyExpireDateUpdateRequest request,
                                                              @AuthenticationPrincipal CustomUserDetails loginUser) {
        var command = userApiKeyRequestConverter.toExpireDateUpdateCommand(apiKeySeq, request);
        apiKeyUseCase.updateExpireDate(command, ApiKeyScope.registerUser(loginUser.getUserSeq()));
        return ApiResponse.success();
    }

    @ExternalApi
    @TenantRestriction(TenantFeature.API_KEY_MANAGE)
    @Operation(summary = "Activate your API key",
            description = "Enables the selected key issued by the signed-in user, subject to its expiration date.")
    @PatchMapping("/{apiKeySeq}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@ParameterDoc(title = "API Key record ID", description = "Numeric apiKeySeq returned by the API Key list. Do not enter the secret API Key value.") @PathVariable Integer apiKeySeq,
                                                      @AuthenticationPrincipal CustomUserDetails loginUser) {
        apiKeyUseCase.activate(apiKeySeq, ApiKeyScope.registerUser(loginUser.getUserSeq()));
        return ApiResponse.success();
    }

    @ExternalApi
    @TenantRestriction(TenantFeature.API_KEY_MANAGE)
    @Operation(summary = "Deactivate your API key",
            description = "Disables authentication with the selected key issued by the signed-in user.")
    @PatchMapping("/{apiKeySeq}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@ParameterDoc(title = "API Key record ID", description = "Numeric apiKeySeq returned by the API Key list. Do not enter the secret API Key value.") @PathVariable Integer apiKeySeq,
                                                        @AuthenticationPrincipal CustomUserDetails loginUser) {
        apiKeyUseCase.deactivate(apiKeySeq, ApiKeyScope.registerUser(loginUser.getUserSeq()));
        return ApiResponse.success();
    }

    @ExternalApi
    @TenantRestriction(TenantFeature.API_KEY_MANAGE)
    @Operation(summary = "Reissue your API key",
            description = "Replaces a key issued by the signed-in user. Copy the new key value from this response.")
    @PostMapping("/{apiKeySeq}/reissue")
    public ResponseEntity<ApiResponse<ApiKeyReissueResponse>> reissue(@ParameterDoc(title = "API Key record ID", description = "Numeric apiKeySeq returned by the API Key list. Do not enter the secret API Key value.") @PathVariable Integer apiKeySeq,
                                                                      @AuthenticationPrincipal CustomUserDetails loginUser) {
        var rawApiKey = apiKeyUseCase.reissue(apiKeySeq, ApiKeyScope.registerUser(loginUser.getUserSeq()));
        return ApiResponse.success(new ApiKeyReissueResponse(rawApiKey));
    }
}
