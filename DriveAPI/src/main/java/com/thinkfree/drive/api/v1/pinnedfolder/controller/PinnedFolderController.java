package com.thinkfree.drive.api.v1.pinnedfolder.controller;

import com.thinkfree.drive.global.annotation.ParameterDoc;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.pinnedfolder.request.PinnedFolderSaveRequest;
import com.thinkfree.drive.api.v1.pinnedfolder.response.PinnedFolderListResponse;
import com.thinkfree.drive.api.v1.pinnedfolder.usecase.PinnedFolderUseCase;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.permission.aspect.annotation.CheckResourcePermission;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/pinned-folders")
@Slf4j
@Tag(name = "Pinned folders")
public class PinnedFolderController {
    
    private final PinnedFolderUseCase pinnedFolderUseCase;
    
    @ExternalApi
    @Operation(summary = "List pinned folders",
            description = "Returns the folders pinned by the signed-in user.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PinnedFolderListResponse>>> listPinnedFolders(@AuthenticationPrincipal CustomUserDetails details){
        var response = pinnedFolderUseCase.listPinnedFolders(details.getUserSeq());
        return ApiResponse.success(response);
    }
    
    @ExternalApi
    @Operation(summary = "Pin a folder",
            description = "Adds the requested folder to the signed-in user's pinned folders.")
    @CheckResourcePermission(SharePermissionType.VIEW)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<Void>> pinFolder(@RequestBody @Valid PinnedFolderSaveRequest request, @AuthenticationPrincipal CustomUserDetails details) {
        pinnedFolderUseCase.pinFolder(request, details.getUserSeq());
        return ApiResponse.created();
    }
    
    @ExternalApi
    @Operation(summary = "Unpin a folder",
            description = "Removes the selected pin from the signed-in user's list while leaving the folder in place.")
    @DeleteMapping("/{pinnedFolderSeq}")
    public ResponseEntity<ApiResponse<Void>> unpinFolder(@ParameterDoc(title = "Pinned-folder entry ID", description = "Numeric pinnedFolderSeq returned by the pinned-folder list, not the folder's resource ID.") @PathVariable Long pinnedFolderSeq, @AuthenticationPrincipal CustomUserDetails details){
        pinnedFolderUseCase.unpinFolder(pinnedFolderSeq, details.getUserSeq());
        return ApiResponse.success();
    }
    
    
}
