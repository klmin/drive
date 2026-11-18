package com.thinkfree.drive.api.v1.admin.user.pendingdelete.controller;

import com.thinkfree.drive.global.annotation.ParameterDoc;
import com.thinkfree.drive.tenant.policy.TenantRestriction;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.user.pendingdelete.converter.AdminPendingDeleteUserRequestConverter;
import com.thinkfree.drive.api.v1.admin.user.pendingdelete.converter.AdminPendingDeleteUserResponseConverter;
import com.thinkfree.drive.api.v1.admin.user.pendingdelete.request.AdminPendingDeletePermanentRequest;
import com.thinkfree.drive.api.v1.admin.user.pendingdelete.request.AdminPendingDeleteUserListRequest;
import com.thinkfree.drive.api.v1.admin.user.pendingdelete.response.AdminPendingDeleteUserListResponse;
import com.thinkfree.drive.application.admin.user.pendingdelete.AdminPendingDeleteUserUseCase;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.tenant.annotation.TenantUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admins/users/pending-delete")
@Tag(name = "Admin - Pending deletion")
public class AdminPendingDeleteUserController {

    private final AdminPendingDeleteUserUseCase adminPendingDeleteUserUseCase;
    private final AdminPendingDeleteUserRequestConverter adminPendingDeleteUserRequestConverter;
    private final AdminPendingDeleteUserResponseConverter adminPendingDeleteUserResponseConverter;

    @ExternalApi
    @Operation(summary = "List users pending deletion",
            description = "Returns a cursor-paginated list of tenant accounts that are pending permanent deletion.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminPendingDeleteUserListResponse>>> findAllCursorPaginated(@ModelAttribute AdminPendingDeleteUserListRequest request, @ModelAttribute @Valid CursorPagingRequest pagingRequest) {
        var command = adminPendingDeleteUserRequestConverter.toListCommand(request, pagingRequest);
        var pageInfo = adminPendingDeleteUserUseCase.findAllCursorPaginated(command);
        return ApiResponse.successWithCursor(adminPendingDeleteUserResponseConverter.toListResponse(pageInfo.list()), adminPendingDeleteUserResponseConverter.toCursorApiPage(pageInfo));
    }

    @ExternalApi
    @Operation(summary = "Restore a pending user",
            description = "Restores the selected account from the pending deletion state.")
    @PatchMapping("/{userSeq}/restore")
    @TenantRestriction(TenantFeature.SUB_ACCOUNT_MANAGE)
    public ResponseEntity<ApiResponse<Void>> restore(@TenantUser @ParameterDoc(title = "User ID", description = "Numeric Drive userSeq returned by the user list. This is not the user's email address.") @PathVariable Integer userSeq){
        adminPendingDeleteUserUseCase.restore(userSeq);
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Permanently delete pending users",
            description = "Permanently removes the selected accounts from the pending deletion list. This operation cannot be undone.")
    @PatchMapping("/permanent-delete")
    @TenantRestriction(TenantFeature.SUB_ACCOUNT_MANAGE)
    public ResponseEntity<ApiResponse<Void>> permanentDelete(@RequestBody @Valid AdminPendingDeletePermanentRequest request,
                                                             @AuthenticationPrincipal CustomUserDetails details){
        adminPendingDeleteUserUseCase.permanentDeleteUsers(adminPendingDeleteUserRequestConverter.toPermanentCommand(request), details.getUserSeq());
        return ApiResponse.success();
    }
}
