package com.thinkfree.drive.api.v1.file.controller;

import com.thinkfree.drive.global.annotation.ParameterDoc;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.response.DownloadLinkResponse;
import com.thinkfree.drive.api.v1.file.converter.FileRequestConverter;
import com.thinkfree.drive.api.v1.file.converter.FileResponseConverter;
import com.thinkfree.drive.api.v1.file.request.*;
import com.thinkfree.drive.api.v1.file.response.FileCopyResponse;
import com.thinkfree.drive.api.v1.file.response.FileRenameResponse;
import com.thinkfree.drive.api.v1.file.response.FileUploadResponse;
import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.application.file.FileDownloadUseCase;
import com.thinkfree.drive.application.file.FileUseCase;
import com.thinkfree.drive.application.resource.ResourceUseCase;
import com.thinkfree.drive.application.trash.TrashUseCase;
import com.thinkfree.drive.global.annotation.AnonymousAccessible;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.RequiredApiScope;
import com.thinkfree.drive.permission.aspect.annotation.CheckResourcePermission;
import com.thinkfree.drive.permission.aspect.enums.DeletePermissionMode;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import com.thinkfree.drive.util.IntellectRequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/files")
@Slf4j
@Tag(name = "Files")
public class FileController {

    private final FileUseCase fileUseCase;
    private final FileDownloadUseCase fileDownloadUseCase;
    private final TrashUseCase trashUseCase;
    private final ResourceUseCase resourceUseCase;
    private final FileRequestConverter fileRequestConverter;
    private final FileResponseConverter fileResponseConverter;

    /** Direct permanent deletion: unlike PATCH /delete, no committed Trash state is exposed. */
    @ExternalApi
    @RequiredApiScope(ApiKeyScope.WRITE)
    @Operation(summary = "Permanently delete a file",
            description = "Permanently deletes the owned file and its versions without moving it to Trash. This cannot be undone.")
    @DeleteMapping("/{resourceSeq}")
    public ResponseEntity<ApiResponse<Void>> deletePermanently(@ParameterDoc(title = "Resource ID", description = "Numeric resourceSeq of the file or folder to operate on. Copy it from the file and folder list.") @PathVariable Long resourceSeq, @AuthenticationPrincipal CustomUserDetails details) {
        trashUseCase.deleteActivePermanently(resourceSeq,details.getUserSeq(),details.getUsername(),com.thinkfree.drive.resource.enums.ResourceType.FILE);
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Upload a file",
            description = "Uploads the supplied file to the requested location and returns information about the created resource.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @AnonymousAccessible
    public ResponseEntity<ApiResponse<FileUploadResponse>> upload(@ModelAttribute @Valid FileUploadRequest request, @AuthenticationPrincipal CustomUserDetails details, HttpServletRequest httpServletRequest) {
        var info = fileUseCase.upload(fileRequestConverter.toUploadCommand(request), details.getUserSeq(), details.isAnonymous(),
                IntellectRequestUtil.isFromIntellect(httpServletRequest));
        return ApiResponse.created(fileResponseConverter.toUploadResponse(info));
    }

    // 인텔릭트에서 사용 — 인텔릭트 미리보기·열기의 본문 로드라 보기 권한으로 검사(/view와 동일 기준)
    @ExternalApi
    @Operation(summary = "Download a file",
            description = "Streams the selected file with its content type, length, and download headers. Drive checks access to the resource.")
    @GetMapping("/{resourceSeq}/download")
    @AnonymousAccessible
    @CheckResourcePermission(value=SharePermissionType.VIEW, isDelete = DeletePermissionMode.ANY)
    public ResponseEntity<StreamingResponseBody> download(FileDownloadRequest request, @AuthenticationPrincipal CustomUserDetails details, HttpServletRequest httpServletRequest){
        var command = fileRequestConverter.toDownloadCommand(request);
        // 인텔릭트 본문 로드는 mode(읽기 전용/편집) 무관하게 문서 열기로 기록
        var streamInfo = IntellectRequestUtil.isFromIntellect(httpServletRequest)
                ? fileDownloadUseCase.open(command, details.getUserSeq())
                : fileDownloadUseCase.download(command, details.getUserSeq());
        var response = fileResponseConverter.toStreamingDownloadResponse(streamInfo);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, response.contentDisposition().toString())
                .header(HttpHeaders.CONTENT_LENGTH, response.contentLength().toString())
                .contentType(response.mediaType())
                .body(response.body());
    }

    @ExternalApi
    @Operation(summary = "Create a file download link",
            description = "Returns a signed link for the selected file when the caller has download permission.")
    @GetMapping("/{resourceSeq}/download-token")
    @AnonymousAccessible
    @CheckResourcePermission(value=SharePermissionType.DOWNLOAD, isDelete = DeletePermissionMode.ANY)
    public ResponseEntity<ApiResponse<DownloadLinkResponse>> generateDownloadUrl(FileDownloadRequest request, @AuthenticationPrincipal CustomUserDetails details){
        var info = fileDownloadUseCase.createSignedUrl(fileRequestConverter.toDownloadCommand(request), details.getUserSeq());
        return ApiResponse.success(fileResponseConverter.toDownloadLinkResponse(info));
    }

    @ExternalApi
    @Operation(summary = "Download a file by token",
            description = "Streams the file associated with the signed download token in the dt query parameter.")
    @GetMapping("/download")
    public ResponseEntity<StreamingResponseBody> downloadByToken(@ParameterDoc(title = "Download token", description = "Token from the download-token response's linkUrl. Enter only the dt value, not the complete URL.") @RequestParam("dt") String token) {
        var response = fileResponseConverter.toStreamingDownloadResponse(fileDownloadUseCase.downloadByToken(token));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, response.contentDisposition().toString())
                .header(HttpHeaders.CONTENT_LENGTH, response.contentLength().toString())
                .contentType(response.mediaType())
                .body(response.body());
    }


    @ExternalApi
    @Operation(summary = "Get file content for preview",
            description = "Streams the selected file for preview when the caller has view permission.")
    @GetMapping("/{resourceSeq}/view")
    @AnonymousAccessible
    @CheckResourcePermission(value=SharePermissionType.VIEW, isDelete = DeletePermissionMode.ANY)
    public ResponseEntity<StreamingResponseBody> view(FileViewRequest request, @AuthenticationPrincipal CustomUserDetails details){
        var response = fileResponseConverter.toStreamingDownloadResponse(
                fileDownloadUseCase.view(fileRequestConverter.toDownloadCommand(request), details.getUserSeq()));
        return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, response.contentDisposition().toString())
                            .header(HttpHeaders.CONTENT_LENGTH, response.contentLength().toString())
                            .contentType(response.mediaType())
                            .body(response.body());
    }

    @ExternalApi
    @Operation(summary = "Move a file to Trash",
            description = "Moves an owned file to Trash, where it can be restored until permanently deleted.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @PatchMapping("/{resourceSeq}/delete")
    // 잠김 검사는 TrashUseCase 가 행 잠금 뒤에 한다(구 @CheckResourceLock 은 트랜잭션 밖이라 확인~이동 사이 경합을 못 막았다)
    public ResponseEntity<ApiResponse<Void>> delete(@ParameterDoc(title = "Resource ID", description = "Numeric resourceSeq of the file or folder to operate on. Copy it from the file and folder list.") @PathVariable Long resourceSeq, @AuthenticationPrincipal CustomUserDetails details) {
        trashUseCase.moveFileToTrash(resourceSeq, details.getUserSeq());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Rename a file",
            description = "Changes the selected file name and returns the resulting name. Renaming a shared file requires edit permission.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @PatchMapping("/{resourceSeq}/rename")
    @CheckResourcePermission(value = SharePermissionType.EDIT, resourceSeq = "#resourceSeq", sharedByUserSeq = "#request.ss")
    // 잠김 검사(ncp=AUTO 면 건너뜀)는 ResourceService.resolveName 이 행 잠금 뒤에 한다
    public ResponseEntity<ApiResponse<FileRenameResponse>> rename(@ParameterDoc(title = "Resource ID", description = "Numeric resourceSeq of the file or folder to operate on. Copy it from the file and folder list.") @PathVariable Long resourceSeq, @RequestBody @Valid FileRenameRequest request, @AuthenticationPrincipal CustomUserDetails details) {
        var info = resourceUseCase.renameFile(resourceSeq, fileRequestConverter.toRenameCommand(request), details.getUserSeq());
        return ApiResponse.success(fileResponseConverter.toRenameResponse(info));
    }

    @ExternalApi
    @Operation(summary = "Move a file",
            description = "Moves the file to the requested destination. The caller needs edit permission on the file and upload permission on the destination.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @PostMapping("/{resourceSeq}/move")
    @CheckResourcePermission(value = SharePermissionType.UPLOAD, resourceSeq = "#request.pfs", sharedByUserSeq = "#request.ts")
    @CheckResourcePermission(value = SharePermissionType.EDIT, resourceSeq = "#resourceSeq", sharedByUserSeq = "#request.ss")
    // 잠김 검사는 ResourceService.fileMove 가 행 잠금 뒤에 한다
    public ResponseEntity<ApiResponse<Void>> move(@ParameterDoc(title = "Resource ID", description = "Numeric resourceSeq of the file or folder to operate on. Copy it from the file and folder list.") @PathVariable Long resourceSeq, @RequestBody FileMoveRequest request, @AuthenticationPrincipal CustomUserDetails details) {
        resourceUseCase.moveFile(resourceSeq, fileRequestConverter.toMoveCommand(request), details.getUserSeq(), details.getUsername());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Copy a file",
            description = "Creates a copy at the requested destination. The caller needs edit permission on the source and upload permission on the destination.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @PostMapping("/{resourceSeq}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    @CheckResourcePermission(value = SharePermissionType.UPLOAD, resourceSeq = "#request.pfs", sharedByUserSeq = "#request.ts")
    @CheckResourcePermission(value = SharePermissionType.EDIT, resourceSeq = "#resourceSeq", sharedByUserSeq = "#request.ss")
    public ResponseEntity<ApiResponse<FileCopyResponse>> copy(@ParameterDoc(title = "Resource ID", description = "Numeric resourceSeq of the file or folder to operate on. Copy it from the file and folder list.") @PathVariable Long resourceSeq, @RequestBody FileCopyRequest request, @AuthenticationPrincipal CustomUserDetails details){
        var info = fileUseCase.copy(resourceSeq, fileRequestConverter.toCopyCommand(request), details.getUserSeq());
        return ApiResponse.created(fileResponseConverter.toCopyResponse(info));
    }

}
