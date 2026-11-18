package com.thinkfree.drive.api.v1.resource.version.controller;

import com.thinkfree.drive.global.annotation.ParameterDoc;
import com.thinkfree.drive.api.page.ApiPage;
import com.thinkfree.drive.api.page.request.PagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.response.DownloadLinkResponse;
import com.thinkfree.drive.api.v1.resource.version.converter.ResourceVersionRequestConverter;
import com.thinkfree.drive.api.v1.resource.version.converter.ResourceVersionResponseConverter;
import com.thinkfree.drive.api.v1.resource.version.request.*;
import com.thinkfree.drive.api.v1.resource.version.response.ResourceVersionListResponse;
import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.application.version.VersionDownloadUseCase;
import com.thinkfree.drive.application.version.VersionUseCase;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.RequiredApiScope;
import com.thinkfree.drive.permission.aspect.annotation.CheckResourcePermission;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.permission.aspect.enums.DeletePermissionMode;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/resources/{resourceSeq}/versions")
@Tag(name = "Versions")
public class ResourceVersionController {

    private final VersionUseCase versionUseCase;
    private final VersionDownloadUseCase versionDownloadUseCase;
    private final ResourceVersionRequestConverter resourceVersionRequestConverter;
    private final ResourceVersionResponseConverter resourceVersionResponseConverter;

    @ExternalApi
    @Operation(summary = "List file versions",
            description = "Returns a page of versions for the selected resource.")
    @GetMapping
    @CheckResourcePermission(value= SharePermissionType.VIEW)
    public ResponseEntity<ApiResponse<List<ResourceVersionListResponse>>> findAllPaginated(ResourceVersionListRequest request,
                                                                                           @ModelAttribute @Valid PagingRequest pagingRequest,
                                                                                           @AuthenticationPrincipal CustomUserDetails details) {
        var command = resourceVersionRequestConverter.toListCommand(request, pagingRequest);
        var pageInfo = versionUseCase.findAllPaginated(command, details.getUserSeq());
        return ApiResponse.success(resourceVersionResponseConverter.toListResponses(pageInfo.list()),
                ApiPage.of(pageInfo.totalCount(), pageInfo.pageIndex(), pageInfo.pageSize()));
    }

    @ExternalApi
    @Operation(summary = "Create a version download link",
            description = "Returns a signed download link for the selected version of the resource.")
    @GetMapping("/{resourceVersionSeq}/download-token")
    @CheckResourcePermission(value=SharePermissionType.DOWNLOAD, isDelete = DeletePermissionMode.ANY)
    public ResponseEntity<ApiResponse<DownloadLinkResponse>> generateDownloadUrl(ResourceVersionDownloadRequest request, @AuthenticationPrincipal CustomUserDetails details){
        var info = versionDownloadUseCase.createSignedUrl(resourceVersionRequestConverter.toDownloadCommand(request), details.getUserSeq());
        return ApiResponse.success(resourceVersionResponseConverter.toDownloadLinkResponse(request.resourceSeq(), info));
    }

    @ExternalApi
    @Operation(summary = "Download a file version by token",
            description = "Streams the version identified by the signed token in the dt query parameter.")
    // {resourceSeq} comes from the class route and is not bound here; document its real type for API clients.
    @Parameter(name = "resourceSeq", in = ParameterIn.PATH, required = true,
            schema = @Schema(type = "integer", format = "int64", title = "Resource ID",
                    description = "Resource ID from the version's download URL. The dt token identifies the version to download."))
    @GetMapping("/download")
    public ResponseEntity<StreamingResponseBody> downloadByToken(@ParameterDoc(title = "Download token", description = "Token from the download-token response's linkUrl. Enter only the dt value, not the complete URL.") @RequestParam("dt") String token) {
        var response = resourceVersionResponseConverter.toStreamingDownloadResponse(versionDownloadUseCase.downloadByToken(token));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, response.contentDisposition().toString())
                .header(HttpHeaders.CONTENT_LENGTH, response.contentLength().toString())
                .contentType(response.mediaType())
                .body(response.body());
    }

    @ExternalApi
    @Operation(summary = "Restore a file version",
            description = "Restores the resource using the contents of the selected version.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @PostMapping("/{resourceVersionSeq}/restore")
    @ResponseStatus(HttpStatus.CREATED)
    @CheckResourcePermission(value = SharePermissionType.EDIT, resourceSeq = "#path.resourceSeq", sharedByUserSeq = "#request.ss")
    // 잠김 검사는 버전 저장(ResourceService.updateWithVersioning)이 행 잠금 뒤에 한다
    public ResponseEntity<ApiResponse<Void>> restoreVersion(ResourceVersionPath path,
                                                            @RequestBody ResourceVersionRestoreRequest request,
                                                            @AuthenticationPrincipal CustomUserDetails details){
        versionUseCase.restoreVersion(path.resourceSeq(), path.resourceVersionSeq(), request.ss(), details.getUserSeq());
        return ApiResponse.created();
    }

    @ExternalApi
    @Operation(summary = "Delete a file version",
            description = "Removes the selected historical version from the specified resource.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @DeleteMapping("/{resourceVersionSeq}")
    public ResponseEntity<ApiResponse<Void>> deleteVersion(ResourceVersionDeleteRequest request, @AuthenticationPrincipal CustomUserDetails details){
        versionUseCase.deleteVersion(request.resourceSeq(), request.resourceVersionSeq(), details.getUserSeq());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Delete a file's version history",
            description = "Removes the selected resource's historical versions through the version deletion workflow.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteAllVersions(ResourceVersionDeleteAllRequest request, @AuthenticationPrincipal CustomUserDetails details) {
        versionUseCase.deleteAllVersions(request.resourceSeq(), details.getUserSeq());
        return ApiResponse.success();
    }

}
