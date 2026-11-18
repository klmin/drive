package com.thinkfree.drive.api.v1.trash.controller;

import com.thinkfree.drive.global.annotation.ParameterDoc;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.trash.converter.TrashRequestConverter;
import com.thinkfree.drive.api.v1.trash.converter.TrashResponseConverter;
import com.thinkfree.drive.api.v1.trash.request.TrashFileRestoreRequest;
import com.thinkfree.drive.api.v1.trash.request.TrashFolderRestoreRequest;
import com.thinkfree.drive.api.v1.trash.request.TrashListRequest;
import com.thinkfree.drive.api.v1.trash.response.TrashListResponse;
import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.application.trash.TrashUseCase;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.RequiredApiScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/trash")
@RequiredArgsConstructor
@Tag(name = "Trash")
public class TrashController {

    private final TrashUseCase trashUseCase;
    private final TrashRequestConverter requestConverter;
    private final TrashResponseConverter responseConverter;

    @ExternalApi
    @Operation(summary = "List items in Trash",
            description = "Returns a page of the signed-in user's trashed files and folders.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TrashListResponse>>> findAllPaginated(@ModelAttribute @Valid TrashListRequest request,
                                                                                 @ModelAttribute @Valid CursorPagingRequest pagingRequest,
                                                                                 @AuthenticationPrincipal CustomUserDetails details){
        var command = requestConverter.toListCommand(request, pagingRequest);
        var pageInfo = trashUseCase.findAllPaginated(command, details.getUserSeq());
        return ApiResponse.successWithCursor(responseConverter.toListResponse(pageInfo.list()), responseConverter.toCursorApiPage(pageInfo));
    }

    @ExternalApi
    @Operation(summary = "Restore a trashed file",
            description = "Restores the selected file from Trash using Drive's restore rules.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @PatchMapping("/files/{resourceSeq}/restore")
    public ResponseEntity<ApiResponse<Void>> restoreFile(@ParameterDoc(title = "Resource ID", description = "Numeric resourceSeq of the file or folder to operate on. Copy it from the file and folder list.") @PathVariable Long resourceSeq, @RequestBody @Valid TrashFileRestoreRequest request,
                                                         @AuthenticationPrincipal CustomUserDetails details){
        trashUseCase.restoreFile(resourceSeq, request.pe(), details.getUserSeq());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Restore a trashed folder",
            description = "Restores the selected folder from Trash using Drive's restore rules.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @PatchMapping("/folders/{resourceSeq}/restore")
    public ResponseEntity<ApiResponse<Void>> restoreFolder(@ParameterDoc(title = "Resource ID", description = "Numeric resourceSeq of the file or folder to operate on. Copy it from the file and folder list.") @PathVariable Long resourceSeq, @RequestBody @Valid TrashFolderRestoreRequest request,
                                                           @AuthenticationPrincipal CustomUserDetails details){
        trashUseCase.restoreFolder(resourceSeq, request.pe(), details.getUserSeq());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Permanently delete a trashed file",
            description = "Removes the selected file from Trash. The file cannot be restored after this operation.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @DeleteMapping("/files/{resourceSeq}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(@ParameterDoc(title = "Resource ID", description = "Numeric resourceSeq of the file or folder to operate on. Copy it from the file and folder list.") @PathVariable Long resourceSeq, @AuthenticationPrincipal CustomUserDetails details){
        trashUseCase.deleteFile(resourceSeq, details.getUserSeq(), details.getUsername());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Permanently delete a trashed folder",
            description = "Removes the selected folder from Trash. The folder and its contents cannot be restored after this operation.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @DeleteMapping("/folders/{resourceSeq}")
    public ResponseEntity<ApiResponse<Void>> deleteFolder(@ParameterDoc(title = "Resource ID", description = "Numeric resourceSeq of the file or folder to operate on. Copy it from the file and folder list.") @PathVariable Long resourceSeq, @AuthenticationPrincipal CustomUserDetails details){
        trashUseCase.deleteFolder(resourceSeq, details.getUserSeq(), details.getUsername());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Empty Trash",
            description = "Permanently deletes the signed-in user's trashed items. The deleted items cannot be restored.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @DeleteMapping("/empty")
    public ResponseEntity<ApiResponse<Void>> emptyTrash(@AuthenticationPrincipal CustomUserDetails details) {
        trashUseCase.emptyTrash(details.getUserSeq());
        return ApiResponse.success();
    }

}
