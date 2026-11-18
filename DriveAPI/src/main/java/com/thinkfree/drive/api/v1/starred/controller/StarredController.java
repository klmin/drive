package com.thinkfree.drive.api.v1.starred.controller;

import com.thinkfree.drive.global.annotation.ParameterDoc;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.starred.converter.StarredRequestConverter;
import com.thinkfree.drive.api.v1.starred.converter.StarredResponseConverter;
import com.thinkfree.drive.api.v1.starred.request.StarredListRequest;
import com.thinkfree.drive.api.v1.starred.request.StarredSaveRequest;
import com.thinkfree.drive.api.v1.starred.response.StarredListResponse;
import com.thinkfree.drive.api.v1.starred.response.StarredSaveResponse;
import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.application.starred.StarredUseCase;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/starred")
@RequiredArgsConstructor
@Tag(name = "Starred")
public class StarredController {

    private final StarredUseCase starredUseCase;
    private final StarredRequestConverter requestConverter;
    private final StarredResponseConverter responseConverter;

    @ExternalApi
    @Operation(summary = "List starred items",
            description = "Returns a page of files and folders starred by the signed-in user.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<StarredListResponse>>> findAllPaginated(@ModelAttribute @Valid StarredListRequest request,
                                                                                   @ModelAttribute @Valid CursorPagingRequest pagingRequest,
                                                                                   @AuthenticationPrincipal CustomUserDetails details){
        var command = requestConverter.toListCommand(request, pagingRequest);
        var pageInfo = starredUseCase.findAllPaginated(command, details.getUserSeq());
        return ApiResponse.successWithCursor(responseConverter.toListResponse(pageInfo.list()), responseConverter.toCursorApiPage(pageInfo));
    }

    @ExternalApi
    @Operation(summary = "Star an item",
            description = "Adds the selected resource to the signed-in user's starred items and returns its star identifier.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @CheckResourcePermission(SharePermissionType.VIEW)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<StarredSaveResponse>> save(@RequestBody @Valid StarredSaveRequest request, @AuthenticationPrincipal CustomUserDetails details){
        var starredSeq = starredUseCase.save(requestConverter.toSaveCommand(request), details.getUserSeq());
        return ApiResponse.created(new StarredSaveResponse(starredSeq));
    }

    @ExternalApi
    @Operation(summary = "Remove a star",
            description = "Removes the selected star from the signed-in user's list while leaving the resource in place.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @DeleteMapping("/{starredSeq}")
    public ResponseEntity<ApiResponse<Void>> delete(@ParameterDoc(title = "Starred entry ID", description = "Numeric starredSeq returned by the starred list, not the resource ID.") @PathVariable Long starredSeq, @AuthenticationPrincipal CustomUserDetails details){
        starredUseCase.delete(starredSeq, details.getUserSeq());
        return ApiResponse.success();
    }

}
