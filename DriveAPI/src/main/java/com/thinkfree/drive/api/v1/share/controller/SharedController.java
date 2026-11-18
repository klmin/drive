package com.thinkfree.drive.api.v1.share.controller;

import com.thinkfree.drive.tenant.policy.TenantRestriction;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.share.converter.SharedRequestConverter;
import com.thinkfree.drive.api.v1.share.converter.SharedResponseConverter;
import com.thinkfree.drive.api.v1.share.request.SharedListRequest;
import com.thinkfree.drive.api.v1.share.response.SharedListResponse;
import com.thinkfree.drive.application.share.ShareTargetUseCase;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shared")
@RequiredArgsConstructor
@Tag(name = "Sharing")
public class SharedController {

    private final ShareTargetUseCase shareTargetUseCase;
    private final SharedRequestConverter sharedRequestConverter;
    private final SharedResponseConverter sharedResponseConverter;

    @ExternalApi
    @Operation(summary = "List shared items",
            description = "Returns a page of shared resources matching the supplied filters for the signed-in user.")
    @GetMapping
    @TenantRestriction(TenantFeature.SHARE)
    public ResponseEntity<ApiResponse<List<SharedListResponse>>> findAllPaginated(@ModelAttribute @Valid SharedListRequest request, @ModelAttribute @Valid CursorPagingRequest pagingRequest,
                                                                                  @AuthenticationPrincipal CustomUserDetails details) {
        var command = sharedRequestConverter.toListCommand(request, pagingRequest);
        var pageInfo = shareTargetUseCase.findAllPaginated(command, details.getUserSeq());
        return ApiResponse.successWithCursor(sharedResponseConverter.toListResponse(pageInfo.list()), sharedResponseConverter.toCursorApiPage(pageInfo));
    }

}
