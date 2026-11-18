package com.thinkfree.drive.api.v1.document.controller;

import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.document.converter.DocumentRequestConverter;
import com.thinkfree.drive.api.v1.document.converter.DocumentResponseConverter;
import com.thinkfree.drive.api.v1.document.request.DocumentCreateRequest;
import com.thinkfree.drive.api.v1.document.response.DocumentCreateResponse;
import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.application.document.DocumentCreateUseCase;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.RequiredApiScope;
import com.thinkfree.drive.permission.aspect.annotation.CheckResourcePermission;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/documents")
@Tag(name = "Documents")
public class DocumentController {

    private final DocumentCreateUseCase documentCreateUseCase;
    private final DocumentRequestConverter documentRequestConverter;
    private final DocumentResponseConverter documentResponseConverter;

    @ExternalApi
    @Operation(summary = "Create a document",
            description = "Creates a document using the requested document type and destination, then returns information about the new item.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CheckResourcePermission(value = SharePermissionType.UPLOAD, resourceSeq = "#request.pfs", sharedByUserSeq = "#request.ss")
    public ResponseEntity<ApiResponse<DocumentCreateResponse>> create(@RequestBody @Valid DocumentCreateRequest request, @AuthenticationPrincipal CustomUserDetails details) {
        var command = documentRequestConverter.toCreateCommand(request);
        var info = documentCreateUseCase.create(command, details.getUserSeq());
        return ApiResponse.created(documentResponseConverter.toCreateResponse(info));
    }

}
