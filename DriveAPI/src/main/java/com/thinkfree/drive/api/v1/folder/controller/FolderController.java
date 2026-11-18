package com.thinkfree.drive.api.v1.folder.controller;

import com.thinkfree.drive.global.annotation.ParameterDoc;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.response.DownloadLinkResponse;
import com.thinkfree.drive.api.v1.folder.converter.FolderRequestConverter;
import com.thinkfree.drive.api.v1.folder.converter.FolderResponseConverter;
import com.thinkfree.drive.api.v1.folder.request.*;
import com.thinkfree.drive.api.v1.folder.response.FolderUploadResponse;
import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.application.folder.FolderDownloadUseCase;
import com.thinkfree.drive.application.folder.FolderUseCase;
import com.thinkfree.drive.application.resource.ResourceUseCase;
import com.thinkfree.drive.application.trash.TrashUseCase;
import com.thinkfree.drive.global.annotation.AnonymousAccessible;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.RequiredApiScope;
import com.thinkfree.drive.permission.aspect.annotation.CheckResourcePermission;
import com.thinkfree.drive.permission.aspect.enums.DeletePermissionMode;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/folders")
@Slf4j
@Tag(name = "Folders")
public class FolderController {

    private final FolderUseCase folderUseCase;
    private final FolderDownloadUseCase folderDownloadUseCase;
    private final TrashUseCase trashUseCase;
    private final ResourceUseCase resourceUseCase;
    private final FolderRequestConverter folderRequestConverter;
    private final FolderResponseConverter folderResponseConverter;

    /** Direct permanent deletion: unlike PATCH /delete, no committed Trash state is exposed. */
    @ExternalApi
    @RequiredApiScope(ApiKeyScope.WRITE)
    @Operation(summary = "Permanently delete a folder",
            description = "Permanently deletes the owned folder and its versions without moving it to Trash. This cannot be undone.")
    @DeleteMapping("/{resourceSeq}")
    public ResponseEntity<ApiResponse<Void>> deletePermanently(@ParameterDoc(title = "Resource ID", description = "Numeric resourceSeq of the file or folder to operate on. Copy it from the file and folder list.") @PathVariable Long resourceSeq, @AuthenticationPrincipal CustomUserDetails details) {
        trashUseCase.deleteActivePermanently(resourceSeq,details.getUserSeq(),details.getUsername(),com.thinkfree.drive.resource.enums.ResourceType.FOLDER);
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Create a folder",
            description = "Creates a folder at the location specified in the request. Creating it in a shared folder requires upload permission.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @CheckResourcePermission(SharePermissionType.UPLOAD)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<FolderUploadResponse>> save(@RequestBody @Valid FolderUploadRequest request, @AuthenticationPrincipal CustomUserDetails details) {
        var info = folderUseCase.create(folderRequestConverter.toCreateCommand(request), details.getUserSeq());
        return ApiResponse.created(folderResponseConverter.toUploadResponse(info));
    }

    @ExternalApi
    @Operation(summary = "Create a folder download link",
            description = "Returns a signed download link for the folder. The caller must have download permission.")
    @GetMapping("/{resourceSeq}/download-token")
    @AnonymousAccessible
    @CheckResourcePermission(value=SharePermissionType.DOWNLOAD, isDelete = DeletePermissionMode.ANY)
    public ResponseEntity<ApiResponse<DownloadLinkResponse>> generateDownloadUrl(FolderDownloadRequest request, @AuthenticationPrincipal CustomUserDetails details){
        var info = folderDownloadUseCase.createSignedUrl(folderRequestConverter.toDownloadCommand(request), details.getUserSeq());
        return ApiResponse.success(folderResponseConverter.toDownloadLinkResponse(info));
    }

    @ExternalApi
    @Operation(summary = "Download a folder by token",
            description = "Streams the folder archive using the download token supplied in the dt query parameter.")
    @AnonymousAccessible
    @GetMapping("/download")
    public ResponseEntity<StreamingResponseBody> downloadByToken(@ParameterDoc(title = "Download token", description = "Token from the download-token response's linkUrl. Enter only the dt value, not the complete URL.") @RequestParam("dt") String token) {
        var response = folderResponseConverter.toStreamingDownloadResponse(folderDownloadUseCase.downloadByToken(token));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, response.contentDisposition().toString())
                .contentType(response.mediaType())
                .body(response.body());
    }

    @ExternalApi
    @Operation(summary = "Move a folder to Trash",
            description = "Moves an owned folder to Trash so the owner can restore it later.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @PatchMapping("/{resourceSeq}/delete")
    // 잠김 검사는 TrashUseCase 가 행 잠금 뒤에 한다(구 @CheckResourceLock 은 트랜잭션 밖이라 확인~이동 사이 경합을 못 막았다)
    public ResponseEntity<ApiResponse<Void>> delete(@ParameterDoc(title = "Resource ID", description = "Numeric resourceSeq of the file or folder to operate on. Copy it from the file and folder list.") @PathVariable Long resourceSeq, @AuthenticationPrincipal CustomUserDetails details) {
        trashUseCase.moveFolderToTrash(resourceSeq, details.getUserSeq());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Rename a folder",
            description = "Changes the name of the specified folder. Renaming a shared folder requires edit permission.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @CheckResourcePermission(value = SharePermissionType.EDIT, resourceSeq = "#resourceSeq", sharedByUserSeq = "#request.ss")
    @PatchMapping("/{resourceSeq}/rename")
    public ResponseEntity<ApiResponse<Void>> rename(@ParameterDoc(title = "Resource ID", description = "Numeric resourceSeq of the file or folder to operate on. Copy it from the file and folder list.") @PathVariable Long resourceSeq, @RequestBody @Valid FolderRenameRequest request, @AuthenticationPrincipal CustomUserDetails details) {
        resourceUseCase.renameFolder(resourceSeq, folderRequestConverter.toRenameCommand(request), details.getUserSeq());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Move a folder",
            description = "Moves the folder to the destination specified in the request. The caller needs edit permission on the source and upload permission on the destination.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @CheckResourcePermission(value = SharePermissionType.UPLOAD, resourceSeq = "#request.pfs", sharedByUserSeq = "#request.ts")
    @CheckResourcePermission(value = SharePermissionType.EDIT, resourceSeq = "#resourceSeq", sharedByUserSeq = "#request.ss")
    // 잠김 검사(하위 포함)는 ResourceService.folderMove 가 행 잠금 뒤에 한다
    @PostMapping("/{resourceSeq}/move")
    public ResponseEntity<ApiResponse<Void>> move(@ParameterDoc(title = "Resource ID", description = "Numeric resourceSeq of the file or folder to operate on. Copy it from the file and folder list.") @PathVariable Long resourceSeq, @RequestBody FolderMoveRequest request, @AuthenticationPrincipal CustomUserDetails details){
        resourceUseCase.moveFolder(resourceSeq, folderRequestConverter.toMoveCommand(request), details.getUserSeq(), details.getUsername());
        return ApiResponse.success();
    }

}
