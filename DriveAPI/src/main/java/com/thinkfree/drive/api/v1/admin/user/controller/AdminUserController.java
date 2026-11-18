package com.thinkfree.drive.api.v1.admin.user.controller;

import com.thinkfree.drive.global.annotation.ParameterDoc;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.converter.AdminRequestConverter;
import com.thinkfree.drive.api.v1.admin.converter.AdminResponseConverter;
import com.thinkfree.drive.api.v1.admin.request.AdminImportCsvRequest;
import com.thinkfree.drive.api.v1.admin.request.AdminUserUpdateRequest;
import com.thinkfree.drive.api.v1.admin.request.UserRegisterRequest;
import com.thinkfree.drive.api.v1.admin.response.AdminImportCsvResponse;
import com.thinkfree.drive.api.v1.admin.response.AdminResponse;
import com.thinkfree.drive.api.v1.admin.response.AdminUserSaveResponse;
import com.thinkfree.drive.api.v1.admin.user.converter.AdminUserRequestConverter;
import com.thinkfree.drive.api.v1.admin.user.converter.AdminUserResponseConverter;
import com.thinkfree.drive.api.v1.admin.user.request.AdminUserBulkStatusRequest;
import com.thinkfree.drive.api.v1.admin.user.request.AdminUserBulkUpsertRequest;
import com.thinkfree.drive.api.v1.admin.user.request.AdminUserListRequest;
import com.thinkfree.drive.api.v1.admin.user.request.AdminUserSearchRequest;
import com.thinkfree.drive.api.v1.admin.user.response.*;
import com.thinkfree.drive.api.v1.user.request.PasswordUpdateRequest;
import com.thinkfree.drive.application.admin.info.AdminImportCsvInfo;
import com.thinkfree.drive.application.admin.user.AdminUserImportUseCase;
import com.thinkfree.drive.application.admin.user.AdminUserQueryUseCase;
import com.thinkfree.drive.application.admin.user.AdminUserUseCase;
import com.thinkfree.drive.application.admin.user.command.AdminUserBulkStatusCommand;
import com.thinkfree.drive.application.admin.user.command.AdminUserSearchCommand;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.tenant.annotation.TenantUser;
import com.thinkfree.drive.tenant.policy.ProtectedAccountRule;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantRestriction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

@RequestMapping("/api/v1/admins/users")
@RestController
@RequiredArgsConstructor
@Tag(name = "Admin - Users")
public class AdminUserController {

    private final AdminUserUseCase adminUserUseCase;
    private final AdminUserRequestConverter adminUserRequestConverter;
    private final AdminUserResponseConverter adminUserResponseConverter;
    private final AdminUserQueryUseCase adminUserQueryUseCase;
    private final AdminUserImportUseCase adminUserImportUseCase;
    private final AdminRequestConverter adminRequestConverter;
    private final AdminResponseConverter adminResponseConverter;

    @ExternalApi
    @Operation(summary = "List tenant users",
            description = "Returns a page of accounts visible to the tenant administrator, using the supplied filters and pagination options.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminUserListResponse>>> findDisplayableUsersPaginated(@ModelAttribute @Valid AdminUserListRequest request, @ModelAttribute @Valid CursorPagingRequest pagingRequest) {
        var command = adminUserRequestConverter.toListCommand(request, pagingRequest);
        var pageInfo = adminUserQueryUseCase.findDisplayableUsersPaginated(command);
        return ApiResponse.successWithCursor(adminUserResponseConverter.toListResponse(pageInfo.list()), adminUserResponseConverter.toCursorApiPage(pageInfo));
    }

    @ExternalApi
    @Operation(summary = "Get a tenant user's storage usage",
            description = "Returns storage usage information for the specified account.")
    @GetMapping("/{userSeq}/storage-usage")
    public ResponseEntity<ApiResponse<AdminUserStorageUsageResponse>> storageUsage(@TenantUser @ParameterDoc(title = "User ID", description = "Numeric Drive userSeq returned by the user list. This is not the user's email address.") @PathVariable Integer userSeq){
        var info = adminUserQueryUseCase.storageUsage(userSeq);
        return ApiResponse.success(adminUserResponseConverter.toAdminUserStorageUsageResponse(info));
    }

    @ExternalApi
    @Operation(summary = "Reset a tenant user's multifactor authentication",
            description = "Clears the selected user's existing multifactor authentication enrollment.")
    @PatchMapping("/{userSeq}/mfa/reset")
    @TenantRestriction(TenantFeature.MFA_SETTING)
    public ResponseEntity<ApiResponse<Void>> resetMfa(@TenantUser @ParameterDoc(title = "User ID", description = "Numeric Drive userSeq returned by the user list. This is not the user's email address.") @PathVariable Integer userSeq) {
        adminUserUseCase.resetMfa(userSeq);
        return ApiResponse.success();
    }

    @ExternalApi
    @TenantRestriction(value = TenantFeature.USER_REGISTER, protectedAccount = ProtectedAccountRule.UPDATE)
    @Operation(summary = "Create or update tenant users in bulk",
            description = "Processes the accounts supplied in the request as a batch and returns the batch results. Changing the roles of a protected account is rejected.")
    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<AdminUserBulkUpsertResponse>> bulkUpsert(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                                               @Valid @RequestBody AdminUserBulkUpsertRequest request) {
        var command = adminUserRequestConverter.toBulkUpsertCommand(request);
        var info = adminUserImportUseCase.bulkUpsert(command, userDetails.getUserSeq());
        return ApiResponse.success(adminUserResponseConverter.toBulkUpsertResponse(info));
    }

    @ExternalApi
    @Operation(summary = "Look up multiple user statuses",
            description = "Looks up the supplied user IDs and returns their account status information.")
    @PostMapping("/bulk-status")
    @TenantRestriction(TenantFeature.USER_REGISTER)
    public ResponseEntity<ApiResponse<List<AdminUserBulkStatusResponse>>> findUserStatusByUserIds(@RequestBody @Valid AdminUserBulkStatusRequest request) {
        var infos = adminUserQueryUseCase.findUserStatusByUserIds(new AdminUserBulkStatusCommand(request.userIds()));
        return ApiResponse.success(adminUserResponseConverter.toBulkStatusResponse(infos));
    }

    @ExternalApi
    @TenantRestriction(protectedAccount = ProtectedAccountRule.MANAGE)
    @Operation(summary = "Delete a tenant user",
            description = "Permanently deletes the selected account immediately. This cannot be undone.")
    @DeleteMapping("/{userSeq}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@AuthenticationPrincipal CustomUserDetails userDetails, @TenantUser @ParameterDoc(title = "User ID", description = "Numeric Drive userSeq returned by the user list. This is not the user's email address.") @PathVariable Integer userSeq) {
        adminUserUseCase.deleteUser(userSeq, userDetails.getUserSeq());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "List a tenant user's activity",
            description = "Returns a page of activity records for the specified tenant user.")
    @GetMapping("/{userSeq}/log")
    public ResponseEntity<ApiResponse<List<AdminUserLogResponse>>> getUserLogs(@TenantUser @ParameterDoc(title = "User ID", description = "Numeric Drive userSeq returned by the user list. This is not the user's email address.") @PathVariable("userSeq") Integer userSeq, @ModelAttribute @Valid CursorPagingRequest pagingRequest) {
        var pageInfo = adminUserQueryUseCase.findUserLogs(userSeq, adminUserRequestConverter.toLogListCommand(pagingRequest));
        return ApiResponse.successWithCursor(adminUserResponseConverter.toAdminUserLogResponse(pageInfo.list()), adminUserResponseConverter.toCursorApiPage(pageInfo));
    }

    @ExternalApi
    @Operation(summary = "Search tenant users",
            description = "Finds accounts that match the supplied keyword, role, and status filters.")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<AdminUserSearchResponse>>> search(@ModelAttribute @Valid AdminUserSearchRequest request) {
        var infos = adminUserQueryUseCase.search(new AdminUserSearchCommand(request.sk(), request.rt(), request.st()));
        return ApiResponse.success(adminUserResponseConverter.toSearchResponse(infos));
    }

    @ExternalApi
    @TenantRestriction(TenantFeature.USER_REGISTER)
    @Operation(summary = "Create a tenant user",
            description = "Creates an account in the current tenant and returns its user identifier.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<AdminUserSaveResponse>> registerUser(@AuthenticationPrincipal CustomUserDetails userDetails, @Valid @ModelAttribute UserRegisterRequest request) {
        var command = adminRequestConverter.toRegisterCommand(request);
        int userSeq = adminUserUseCase.registerUser(command, userDetails.getUserSeq());
        return ApiResponse.created(new AdminUserSaveResponse(userSeq));
    }

    @ExternalApi
    @TenantRestriction(protectedAccount = ProtectedAccountRule.MANAGE)
    @Operation(summary = "Withdraw a tenant user",
            description = "Moves the account to pending deletion. It is permanently deleted after 30 days unless restored.")
    @PatchMapping("/{userSeq}/withdraw")
    public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticationPrincipal CustomUserDetails userDetails, @TenantUser @ParameterDoc(title = "User ID", description = "Numeric Drive userSeq returned by the user list. This is not the user's email address.") @PathVariable("userSeq") Integer userSeq) {
        adminUserUseCase.withdraw(userDetails.getUserSeq(), userSeq);
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Get a tenant user",
            description = "Returns the administrator view of the account identified by userSeq.")
    @GetMapping("/{userSeq}")
    public ResponseEntity<ApiResponse<AdminResponse>> getUser(@TenantUser @ParameterDoc(title = "User ID", description = "Numeric Drive userSeq returned by the user list. This is not the user's email address.") @PathVariable("userSeq") Integer userSeq) {
        var info = adminUserQueryUseCase.getUser(userSeq);
        return ApiResponse.success(adminResponseConverter.toAdminResponse(info));
    }

    @ExternalApi
    @TenantRestriction(protectedAccount = ProtectedAccountRule.UPDATE)
    @Operation(summary = "Update a tenant user",
            description = "Applies the permitted profile and account changes to the selected tenant user. Changing the roles of a protected account is rejected.")
    @PatchMapping(value = "/{userSeq}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> updateUserByAdmin(@AuthenticationPrincipal CustomUserDetails userDetails, @TenantUser @ParameterDoc(title = "User ID", description = "Numeric Drive userSeq returned by the user list. This is not the user's email address.") @PathVariable("userSeq") Integer userSeq, @Valid @ModelAttribute AdminUserUpdateRequest request) {
        adminUserUseCase.updateUserByAdmin(userDetails.getUserSeq(), userSeq, adminRequestConverter.toUpdateCommand(request));
        return ApiResponse.success();
    }

    @ExternalApi
    @TenantRestriction(protectedAccount = ProtectedAccountRule.MANAGE)
    @Operation(summary = "Set a tenant user's password",
            description = "Replaces the selected user's password with the new value supplied by the administrator.")
    @PatchMapping("/{userSeq}/password")
    public ResponseEntity<ApiResponse<Void>> updatePassword(@TenantUser @ParameterDoc(title = "User ID", description = "Numeric Drive userSeq returned by the user list. This is not the user's email address.") @PathVariable("userSeq") Integer userSeq, @Valid @RequestBody PasswordUpdateRequest request, HttpServletRequest httpRequest) {
        adminUserUseCase.updatePasswordByAdmin(userSeq, request.getNewPwd(), httpRequest);
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Activate a tenant user",
            description = "Changes the selected tenant account to active so it can sign in when other requirements are met.")
    @PatchMapping("/{userSeq}/activate")
    @TenantRestriction(TenantFeature.SUB_ACCOUNT_MANAGE)
    public ResponseEntity<ApiResponse<Void>> activateUser(@AuthenticationPrincipal CustomUserDetails userDetails, @TenantUser @ParameterDoc(title = "User ID", description = "Numeric Drive userSeq returned by the user list. This is not the user's email address.") @PathVariable("userSeq") Integer userSeq) {
        adminUserUseCase.activateUser(userDetails.getUserSeq(), userSeq);
        return ApiResponse.success();
    }

    @ExternalApi
    @TenantRestriction(protectedAccount = ProtectedAccountRule.MANAGE)
    @Operation(summary = "Deactivate a tenant user",
            description = "Deactivates the selected tenant account and prevents it from signing in.")
    @PatchMapping("/{userSeq}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(@AuthenticationPrincipal CustomUserDetails userDetails, @TenantUser @ParameterDoc(title = "User ID", description = "Numeric Drive userSeq returned by the user list. This is not the user's email address.") @PathVariable("userSeq") Integer userSeq) {
        adminUserUseCase.deactivateUser(userDetails.getUserSeq(), userSeq);
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Download the user import template",
            description = "Downloads a sample CSV with the columns expected by the user import operation.")
    @GetMapping("/import/csv/sample")
    public ResponseEntity<StreamingResponseBody> importCsvSample(){
        var response = adminUserImportUseCase.importCsvSample();
        return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, response.contentDisposition().toString())
                            .header(HttpHeaders.CONTENT_LENGTH, response.contentLength().toString())
                            .contentType(response.mediaType())
                            .body(response.body());
    }

    @ExternalApi
    @TenantRestriction({TenantFeature.USER_REGISTER, TenantFeature.MAIL})
    @Operation(summary = "Import users from CSV",
            description = "Uploads a CSV file and processes its rows through the tenant's user import workflow.")
    @PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<?> importCsv(@ModelAttribute @Valid AdminImportCsvRequest request, @AuthenticationPrincipal CustomUserDetails customUserDetails){
        var info = adminUserImportUseCase.importCsv(adminRequestConverter.toImportCsvCommand(request), customUserDetails.getUserSeq());

        if (info instanceof AdminImportCsvInfo.Fail fail) {
            var r = fail.response();
            return ResponseEntity.badRequest()
                                 .header(HttpHeaders.CONTENT_DISPOSITION, r.contentDisposition().toString())
                                 .header(HttpHeaders.CONTENT_LENGTH, r.contentLength().toString())
                                 .contentType(r.mediaType())
                                 .body(r.body());
        }

        var success = (AdminImportCsvInfo.Success) info;
        return ApiResponse.created(new AdminImportCsvResponse.Success(success.insertCount()));
    }

}
