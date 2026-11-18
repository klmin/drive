package com.thinkfree.drive.api.v1.share.target.controller;

import com.thinkfree.drive.global.annotation.ParameterDoc;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.share.target.converter.ShareTargetRequestConverter;
import com.thinkfree.drive.api.v1.share.target.request.ShareTargetUpsertRequest;
import com.thinkfree.drive.apikey.enums.ApiKeyScope;
import com.thinkfree.drive.application.share.ShareTargetUseCase;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.RequiredApiScope;
import com.thinkfree.drive.permission.aspect.annotation.CheckSharePermission;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantRestriction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shares/{shareSeq}/targets")
@RequiredArgsConstructor
@Tag(name = "Sharing")
public class ShareTargetController {

    private final ShareTargetUseCase shareTargetUseCase;
    private final ShareTargetRequestConverter shareTargetRequestConverter;

    @ExternalApi
    @TenantRestriction(TenantFeature.SHARE)
    @Operation(summary = "Replace share recipients",
            description = "Replaces the recipient assignments for the selected share with the set supplied in the request.")
    @RequiredApiScope(ApiKeyScope.WRITE)
    @CheckSharePermission(shareSeq = "#shareSeq", sharedByUserSeq = "#request.ss")
    @PutMapping
    public ResponseEntity<ApiResponse<Void>> replaceShareTargets(@ParameterDoc(title = "Share ID", description = "Numeric shareSeq returned by the share list. This identifies the sharing record, not the file.") @PathVariable Long shareSeq, @RequestBody @Valid ShareTargetUpsertRequest request, @AuthenticationPrincipal CustomUserDetails details) {
        var command = shareTargetRequestConverter.toReplaceCommand(request);
        shareTargetUseCase.replaceShareTargets(shareSeq, command, details.getUserSeq(), details.getUserId(), details.getUsername());
        return ApiResponse.success();
    }
}
