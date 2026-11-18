package com.thinkfree.drive.api.v1.recent.controller;

import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.recent.converter.RecentRequestConverter;
import com.thinkfree.drive.api.v1.recent.converter.RecentResponseConverter;
import com.thinkfree.drive.api.v1.recent.request.RecentListRequest;
import com.thinkfree.drive.api.v1.recent.request.RecentSaveRequest;
import com.thinkfree.drive.api.v1.recent.response.RecentListResponse;
import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.application.recent.RecentUseCase;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.RequiredApiScope;
import com.thinkfree.drive.permission.aspect.annotation.CheckResourcePermission;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recent")
@RequiredArgsConstructor
@Tag(name = "Recent")
public class RecentController {

    private final RecentUseCase recentUseCase;
    private final RecentRequestConverter requestConverter;
    private final RecentResponseConverter responseConverter;

    @ExternalApi
    @Operation(summary = "List recent items",
            description = "Returns a page of the signed-in user's recent items.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<RecentListResponse>>> findAllPaginated(@ModelAttribute @Valid RecentListRequest request,
                                                                                  @ModelAttribute @Valid CursorPagingRequest pagingRequest,
                                                                                  @AuthenticationPrincipal CustomUserDetails details){
        var command = requestConverter.toListCommand(request, pagingRequest);
        var pageInfo = recentUseCase.findAllPaginated(command, details.getUserSeq());
        return ApiResponse.successWithCursor(responseConverter.toListResponse(pageInfo.list()), responseConverter.toCursorApiPage(pageInfo));
    }


    @ExternalApi
    @Operation(summary = "Record a recent item",
            description = "Records the supplied resource in the signed-in user's recent items.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @CheckResourcePermission(SharePermissionType.VIEW)
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> save(@RequestBody @Valid RecentSaveRequest request, @AuthenticationPrincipal CustomUserDetails details){
        recentUseCase.save(requestConverter.toSaveCommand(request), details.getUserSeq());
        return ApiResponse.success();
    }

}
