package com.thinkfree.drive.api.v1.resource.controller;

import com.thinkfree.drive.global.annotation.ParameterDoc;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.response.DownloadLinkResponse;
import com.thinkfree.drive.api.v1.resource.converter.ResourceRequestConverter;
import com.thinkfree.drive.api.v1.resource.converter.ResourceResponseConverter;
import com.thinkfree.drive.api.v1.resource.request.*;
import com.thinkfree.drive.api.v1.resource.response.ResourceDetailResponse;
import com.thinkfree.drive.api.v1.resource.response.ResourceExistsResponse;
import com.thinkfree.drive.api.v1.resource.response.ResourceListResponse;
import com.thinkfree.drive.api.v1.resource.response.ResourceTreeResponse;
import com.thinkfree.drive.api.v1.resource.response.ResourceShareLinkResponse;
import com.thinkfree.drive.application.resource.ResourceDownloadUseCase;
import com.thinkfree.drive.application.resource.ResourceUseCase;
import com.thinkfree.drive.global.annotation.AnonymousAccessible;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.permission.aspect.annotation.CheckResourcePermission;
import com.thinkfree.drive.permission.aspect.enums.DeletePermissionMode;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantFeatureRequired;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/resources")
@Slf4j
@Tag(name = "Resources")
public class ResourceController {

    private final ResourceUseCase resourceUseCase;
    private final ResourceDownloadUseCase resourceDownloadUseCase;
    private final ResourceRequestConverter requestConverter;
    private final ResourceResponseConverter responseConverter;
    
    @ExternalApi
    @Operation(summary = "Get file or folder details",
            description = "Returns information about the selected resource for the current user and share context.")
    @AnonymousAccessible
    @CheckResourcePermission(value=SharePermissionType.VIEW, isDelete = DeletePermissionMode.ANY)
    @GetMapping("/{resourceSeq}")
    public ResponseEntity<ApiResponse<ResourceDetailResponse>> findById(ResourceFindRequest request, @AuthenticationPrincipal CustomUserDetails details) {
        var info = resourceUseCase.findById(requestConverter.toFindCommand(request), details.getUserSeq());
        return ApiResponse.success(responseConverter.toDetailResponse(info));
    }

    @ExternalApi
    @Operation(summary = "List files and folders",
            description = "Returns a page of resources matching the requested folder and list filters.")
    @AnonymousAccessible
    @CheckResourcePermission(value = SharePermissionType.VIEW, resourceSeq = "#request.pfs", sharedByUserSeq = "#request.ss")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ResourceListResponse>>> findAllPaginated(@ModelAttribute @Valid ResourceListRequest request, @ModelAttribute @Valid CursorPagingRequest pagingRequest,
                                                                                    @AuthenticationPrincipal CustomUserDetails details) {
        var command = requestConverter.toSearchCommand(request, pagingRequest);
        var pageInfo = resourceUseCase.search(command, details.getUserSeq());
        return ApiResponse.successWithCursor(responseConverter.toListResponse(pageInfo.list()), responseConverter.toCursorApiPage(pageInfo));
    }

    @ExternalApi
    @Operation(summary = "Check for a duplicate name",
            description = "Checks whether an item with the same name already exists in the target folder.")
    @AnonymousAccessible
    @CheckResourcePermission(value = SharePermissionType.VIEW, resourceSeq = "#request.pfs", sharedByUserSeq = "#request.ss")
    @GetMapping("/exists")
    public ResponseEntity<ApiResponse<ResourceExistsResponse>> exists(@ModelAttribute @Valid ResourceExistsRequest request, @AuthenticationPrincipal CustomUserDetails details){
        var info = resourceUseCase.exists(requestConverter.toExistsCommand(request), details.getUserSeq());
        return ApiResponse.success(responseConverter.toExistsResponse(info));
    }

    @ExternalApi
    @Operation(summary = "Create a multi-item download link",
            description = "Creates a signed link for downloading the selected files and folders together.")
    @AnonymousAccessible
    @PostMapping("/download-token")
    public ResponseEntity<ApiResponse<DownloadLinkResponse>> generateDownloadUrl(@Valid @RequestBody ResourceDownloadRequest request, @AuthenticationPrincipal CustomUserDetails details){
        var info = resourceDownloadUseCase.createSignedUrl(requestConverter.toDownloadCommand(request), details.getUserSeq(), details.isAnonymous());
        return ApiResponse.success(responseConverter.toDownloadLinkResponse(info));
    }

    @ExternalApi
    @Operation(summary = "Download multiple items by token",
            description = "Streams the archive identified by the signed token in the dt query parameter.")
    @AnonymousAccessible
    @GetMapping("/download")
    public ResponseEntity<StreamingResponseBody> downloadByToken(@ParameterDoc(title = "Download token", description = "Token from the download-token response's linkUrl. Enter only the dt value, not the complete URL.") @RequestParam("dt") String token) {
        var response = responseConverter.toStreamingDownloadResponse(resourceDownloadUseCase.downloadMultiByToken(token));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, response.contentDisposition().toString())
                .contentType(response.mediaType())
                .body(response.body());
    }

    @ExternalApi
    @Operation(summary = "Get the folder tree",
            description = "Returns the folder hierarchy available to the signed-in user.")
    @GetMapping("/tree")
    public ResponseEntity<ApiResponse<List<ResourceTreeResponse>>> findResourceTree(@AuthenticationPrincipal CustomUserDetails details){
        var infos = resourceUseCase.findResourceTree(details.getUserSeq());
        return ApiResponse.success(responseConverter.toResourceTreeResponse(infos));
    }

    @ExternalApi
    @Operation(summary = "Get a resource sharing link",
            description = "Returns a sharing link for the selected file or folder using the request's share context.")
    @AnonymousAccessible
    @CheckResourcePermission(value = SharePermissionType.VIEW)
    @GetMapping("/{resourceSeq}/link")
    @TenantFeatureRequired(TenantFeature.SHARE)
    public ResponseEntity<ApiResponse<ResourceShareLinkResponse>> copyShareLink(@ModelAttribute ResourceSharedByUserRequest request,
                                                                        @AuthenticationPrincipal CustomUserDetails details) {
        var info = resourceUseCase.copyShareLink(request.resourceSeq(), request.ss(), details.getUserSeq());
        return ApiResponse.success(responseConverter.toShareLinkResponse(info));
    }

    @ExternalApi
    @Operation(summary = "Lock a resource",
            description = "Locks the selected resource for the current user so conflicting changes can be blocked.")
    @PatchMapping("/{resourceSeq}/lock")
    @CheckResourcePermission(value = SharePermissionType.EDIT, resourceSeq = "#resourceSeq", sharedByUserSeq = "#request.ss")
    public ResponseEntity<ApiResponse<Void>> lock(@ParameterDoc(title = "Resource ID", description = "Numeric resourceSeq of the file or folder to operate on. Copy it from the file and folder list.") @PathVariable Long resourceSeq, @RequestBody ResourceLockRequest request,
                                                  @AuthenticationPrincipal CustomUserDetails details) {
        resourceUseCase.lock(resourceSeq, request.ss(), details.getUserSeq());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Unlock a resource",
            description = "Releases the lock on the selected resource when the current user is allowed to remove it.")
    @PatchMapping("/{resourceSeq}/unlock")
    @CheckResourcePermission(value = SharePermissionType.EDIT, resourceSeq = "#resourceSeq", sharedByUserSeq = "#request.ss")
    public ResponseEntity<ApiResponse<Void>> unlock(@ParameterDoc(title = "Resource ID", description = "Numeric resourceSeq of the file or folder to operate on. Copy it from the file and folder list.") @PathVariable Long resourceSeq, @RequestBody ResourceLockRequest request,
                                                    @AuthenticationPrincipal CustomUserDetails details) {
        resourceUseCase.unlock(resourceSeq, request.ss(), details.getUserSeq());
        return ApiResponse.success();
    }

}
