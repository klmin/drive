package com.thinkfree.drive.api.v1.share.controller;

import com.thinkfree.drive.global.annotation.ParameterDoc;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.share.converter.ShareRequestConverter;
import com.thinkfree.drive.api.v1.share.converter.ShareResponseConverter;
import com.thinkfree.drive.api.v1.share.request.*;
import com.thinkfree.drive.api.v1.share.response.*;
import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.application.share.ShareUseCase;
import com.thinkfree.drive.global.annotation.AnonymousAccessible;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.RequiredApiScope;
import com.thinkfree.drive.permission.aspect.annotation.CheckResourcePermission;
import com.thinkfree.drive.permission.aspect.annotation.CheckSharePermission;
import com.thinkfree.drive.permission.enums.SharePermissionType;
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

import com.thinkfree.drive.global.ratelimit.LoginRateLimitGuard;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/v1/shares")
@RequiredArgsConstructor
@Tag(name = "Sharing")
public class ShareController {

    private final ShareUseCase shareUseCase;
    private final ShareRequestConverter shareRequestConverter;
    private final ShareResponseConverter shareResponseConverter;
    private final LoginRateLimitGuard loginRateLimitGuard;

    @ExternalApi
    @TenantRestriction(TenantFeature.SHARE)
    @Operation(summary = "Create a share",
            description = "Creates a share with the requested recipients or link settings and returns information about the new share.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<ShareSaveResponse>> save(@RequestBody @Valid ShareSaveRequest request, @AuthenticationPrincipal CustomUserDetails details) {
        var command = shareRequestConverter.toSaveCommand(request);
        var info = shareUseCase.save(command, details.getUserSeq(), details.getUserId(), details.getUsername());
        return ApiResponse.created(shareResponseConverter.toShareSaveResponse(info));
    }

    @ExternalApi
    @TenantRestriction(TenantFeature.SHARE)
    @Operation(summary = "Update a share",
            description = "Applies the supplied settings to the selected share.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @PatchMapping("/{shareSeq}")
    public ResponseEntity<ApiResponse<Void>> update(@ParameterDoc(title = "Share ID", description = "Numeric shareSeq returned by the share list. This identifies the sharing record, not the file.") @PathVariable Long shareSeq, @RequestBody @Valid ShareUpdateRequest request, @AuthenticationPrincipal CustomUserDetails details){
        var command = shareRequestConverter.toUpdateCommand(request);
        shareUseCase.update(shareSeq, command, details.getUserSeq(), details.getUserId(), details.getUsername());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Get share details",
            description = "Returns the selected share's configuration and details available to the signed-in user.")
    @CheckSharePermission(value= SharePermissionType.VIEW)
    @GetMapping("/{shareSeq}")
    @TenantRestriction(TenantFeature.SHARE)
    public ResponseEntity<ApiResponse<ShareDetailResponse>> detail(@ModelAttribute SharePathRequest request, @AuthenticationPrincipal CustomUserDetails details) {
        var command = shareRequestConverter.toDetailCommand(request);
        var info = shareUseCase.detail(command, details.getUserSeq());
        return ApiResponse.success(shareResponseConverter.toShareDetailResponse(info));
    }

    @ExternalApi
    @TenantRestriction(TenantFeature.SHARE)
    @Operation(summary = "Remove a share",
            description = "Removes the selected share without deleting the shared resource.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @DeleteMapping("/{shareSeq}")
    public ResponseEntity<ApiResponse<Void>> delete(@ParameterDoc(title = "Share ID", description = "Numeric shareSeq returned by the share list. This identifies the sharing record, not the file.") @PathVariable Long shareSeq, @AuthenticationPrincipal CustomUserDetails details) {
        shareUseCase.delete(shareSeq, details.getUserSeq(), details.getUsername());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "List share recipients",
            description = "Returns users associated with the selected share.")
    @CheckSharePermission(SharePermissionType.VIEW)
    @GetMapping("/{shareSeq}/users")
    @TenantRestriction(TenantFeature.SHARE)
    public ResponseEntity<ApiResponse<List<ShareUserListResponse>>> findShareUsers(@ModelAttribute SharePathRequest request, @AuthenticationPrincipal CustomUserDetails details) {
        var command = shareRequestConverter.toDetailCommand(request);
        var infos = shareUseCase.findShareUsers(command, details.getUserSeq());
        return ApiResponse.success(shareResponseConverter.toShareUsersResponse(infos));
    }

    @ExternalApi
    @Operation(summary = "Search share recipients",
            description = "Finds users matching the supplied criteria. When shareSeq is provided, the search uses that share as context.")
    @CheckSharePermission(value=SharePermissionType.SHARE, isCheckShareSeq = false)
    @GetMapping(value={"/{shareSeq}/users/search", "/users/search"})
    @TenantRestriction(TenantFeature.SHARE)
    public ResponseEntity<ApiResponse<ShareUserSearchResponse>> searchUsers(@ModelAttribute @Valid ShareUserSearchRequest request, @AuthenticationPrincipal CustomUserDetails details) {
        var command = shareRequestConverter.toUserSearchCommand(request);
        var infos = shareUseCase.searchUsers(command, details.getUserSeq());
        return ApiResponse.success(shareResponseConverter.toShareUserSearchResponse(infos));
    }

    @ExternalApi
    @TenantRestriction(TenantFeature.SHARE)
    @Operation(summary = "Open a share link",
            description = "Resolves the supplied link token and returns share information when the access rules allow it.")
    @AnonymousAccessible
    @GetMapping("/link/{token}")
    public ResponseEntity<ApiResponse<ShareLinkAccessResponse>> accessLink(@ModelAttribute ShareLinkAccessRequest request, @AuthenticationPrincipal CustomUserDetails details, HttpServletRequest httpRequest) {
        // 익명 세션은 모두 같은 익명 계정(userSeq)이라 사용자 기준이면 방문자 전체가 한 버킷 — IP 기준으로 센다
        loginRateLimitGuard.checkShareLinkAccess(details == null || details.isAnonymous() ? null : details.getUserSeq(), httpRequest);
        // 미인증(익명) 허용 경로 — details가 null일 수 있어 nullable 해체로 넘긴다
        var info = shareUseCase.accessShareLinkByToken(request.token(),
                details == null ? null : details.getUserSeq(),
                details == null ? null : details.isAnonymous(),
                details == null ? null : details.getTenantSeq());
        return ApiResponse.success(shareResponseConverter.toShareLinkAccessResponse(info));
    }


     /**
     * 리소스 계층 구조 내에서 등록 전 사용 가능한 공유 유형을 조회한다.
     * 주어진 리소스(userSeq, resourceSeq의 hierarchy)에 대해 이미 공유 이력이 존재하면
     * 해당 공유 유형을 반환하고 없으면 전체 공유 유형 목록을 반환한다.
     *
     * @param request resourceSeq (pathVariable)
     * @param details 요청 user Details
     * @return 사용 가능한 공유 유형 response class
     */
    @ExternalApi
    @Operation(summary = "List available sharing types",
            description = "Returns the sharing options available for the specified resource under the current user's permissions and tenant rules.")
    @CheckResourcePermission(value = SharePermissionType.VIEW, resourceSeq = "#request.resourceSeq", sharedByUserSeq = "#request.ss")
    @GetMapping("/{resourceSeq}/share-types")
    @TenantRestriction(TenantFeature.SHARE)
    public ResponseEntity<ApiResponse<ShareTypesResponse>> availableShareTypes(@ModelAttribute ShareTypesRequest request, @AuthenticationPrincipal CustomUserDetails details){
        var command = shareRequestConverter.toTypesCommand(request);
        var info = shareUseCase.availableShareTypes(command, details.getUserSeq());
        return ApiResponse.success(shareResponseConverter.toShareTypesResponse(info));
    }

}
