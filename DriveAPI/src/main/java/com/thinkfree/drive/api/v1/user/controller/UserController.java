package com.thinkfree.drive.api.v1.user.controller;

import com.thinkfree.drive.global.annotation.ParameterDoc;
import com.thinkfree.drive.tenant.policy.TenantFeatureRequired;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.response.StreamingDownloadResponse;
import com.thinkfree.drive.api.v1.user.request.*;
import com.thinkfree.drive.api.v1.user.response.UserQuotaResponse;
import com.thinkfree.drive.api.v1.user.response.UserResponse;
import com.thinkfree.drive.api.v1.user.response.UserSearchResponse;
import com.thinkfree.drive.api.v1.user.usecase.UserUseCase;
import com.thinkfree.drive.global.annotation.AnonymousAccessible;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.user.dto.ProfileImageResult;
import com.thinkfree.drive.user.dto.UserUpdateDto;
import com.thinkfree.drive.user.service.UserService;
import com.thinkfree.drive.util.ResourceUtil;
import com.thinkfree.drive.tenant.policy.ProtectedAccountRule;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantRestriction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@Tag(name = "Account")
public class UserController {
    
    private final UserUseCase userUseCase;
    private final UserService userService;

    @ExternalApi
    @Operation(summary = "Get your profile",
            description = "Returns the signed-in user's account profile.")
    @AnonymousAccessible
    @GetMapping
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(@AuthenticationPrincipal CustomUserDetails userDetails) {
        UserResponse response = userUseCase.getCurrentUser(userDetails.getUserSeq());
        return ApiResponse.success(response);
    }

    @ExternalApi
    @Operation(summary = "Check a user ID",
            description = "Returns 200 when the user ID is available in the current tenant, or DUPLICATE_EMAIL if it is already in use.")
    @GetMapping("/exists")
    public ResponseEntity<ApiResponse<Void>> isExistsUser(@Valid @ModelAttribute UserExistsRequest request) {
        userService.exists(TenantContext.getOrThrow(), request.getUserId());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Update your profile",
            description = "Applies the supplied profile changes to the signed-in user's account.")
    @PatchMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> updateCurrentUser(@AuthenticationPrincipal CustomUserDetails userDetails, @Valid @ModelAttribute UserUpdateRequest request) {
        userService.updateCurrentUser(userDetails.getUserSeq(), toUpdateDto(request));
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Get your profile image",
            description = "Returns the signed-in user's profile image as an image response.")
    @GetMapping("/profile-image")
    public ResponseEntity<StreamingResponseBody> downloadProfileImage(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return toProfileImageResponse(userService.downloadProfileImage(userDetails.getUserSeq()));
    }

    @ExternalApi
    @TenantRestriction(protectedAccount = ProtectedAccountRule.SELF_PASSWORD)
    @Operation(summary = "Change your password",
            description = "Validates the current password before saving the requested new password.")
    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<Void>> updatePassword(@AuthenticationPrincipal CustomUserDetails userDetails, @Valid @RequestBody PasswordUpdateRequest request, HttpServletRequest httpRequest) {
        userService.updatePassword(userDetails.getUserSeq(), userDetails.getUserId(), request.getCurrentPwd(), request.getNewPwd(), httpRequest);
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Get your storage usage",
            description = "Returns the signed-in user's storage quota and usage information.")
    @GetMapping("/quota")
    public ResponseEntity<ApiResponse<UserQuotaResponse>> getCurrentQuota(@AuthenticationPrincipal CustomUserDetails userDetails) {
        UserQuotaResponse response = userUseCase.getCurrentQuota(userDetails.getUserSeq());
        return ApiResponse.success(response);
    }

    @ExternalApi
    @Operation(summary = "Get a user's profile image",
            description = "Returns the profile image for the account identified by userSeq.")
    @GetMapping("/{userSeq}/profile-image")
    public ResponseEntity<StreamingResponseBody> downloadProfileImage(@ParameterDoc(title = "User ID", description = "Numeric Drive userSeq returned by the user list. This is not the user's email address.") @PathVariable("userSeq") Integer userSeq) {
        return toProfileImageResponse(userService.downloadProfileImage(userSeq));
    }

    @ExternalApi
    @Operation(summary = "Get an anonymous avatar",
            description = "Returns the avatar image selected by the animal and color parameters.")
    @GetMapping("/anonymous-profile-image")
    public ResponseEntity<StreamingResponseBody> downloadAnonymousProfileImage(
            @ParameterDoc(title = "Avatar animal index", description = "Animal icon number for the generated avatar, from 1 to 30.") @RequestParam("animal") int animal,
            @ParameterDoc(title = "Avatar color index", description = "Color number for the generated avatar, from 1 to 30.") @RequestParam("color") int color) {
        return toDownloadResponse(userService.downloadAnonymousProfileImage(animal, color));
    }
    
    @ExternalApi
    @Operation(summary = "Search users",
            description = "Returns users visible to the signed-in user that match the supplied search criteria.")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<UserSearchResponse>>> search(@AuthenticationPrincipal CustomUserDetails userDetails, @ModelAttribute @Valid UserSearchRequest request){
        var response = userUseCase.search(request, userDetails.getUserSeq());
        return ApiResponse.success(response);
    }

    @ExternalApi
    @TenantRestriction(value = TenantFeature.MAIL, protectedAccount = ProtectedAccountRule.SELF_PASSWORD)
    @Operation(summary = "Request a password reset link",
            description = "Requests a password reset email for the user ID supplied in the request.")
    @PostMapping("/password/reset-link")
    public ResponseEntity<ApiResponse<Void>> sendPasswordResetLink(@Valid @RequestBody PasswordResetRequest request, HttpServletRequest httpRequest) {
        userService.sendPasswordResetLink(request.getUserId(), httpRequest);
        return ApiResponse.success();
    }

    @ExternalApi
    @TenantRestriction(value = TenantFeature.MAIL, protectedAccount = ProtectedAccountRule.SELF_PASSWORD)
    @Operation(summary = "Request your password reset link",
            description = "Requests a password reset email for the signed-in user's account.")
    @PostMapping("/password/reset-link/me")
    public ResponseEntity<ApiResponse<Void>> sendMyPasswordResetLink(@AuthenticationPrincipal CustomUserDetails userDetails, HttpServletRequest httpRequest) {
        userService.sendPasswordResetLink(userDetails.getUserSeq(), httpRequest);
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Verify a password reset token",
            description = "Checks whether the supplied token is valid before a password reset is submitted.")
    @GetMapping("/password/reset-link/verify")
    @TenantFeatureRequired(TenantFeature.MAIL)
    public ResponseEntity<ApiResponse<Void>> verifyPasswordResetToken(@ParameterDoc(title = "Password reset token", description = "Token from the password-reset link to verify.") @RequestParam String token) {
        userService.verifyPasswordResetToken(token);
        return ApiResponse.success();
    }

    @ExternalApi
    @TenantRestriction(protectedAccount = ProtectedAccountRule.SELF_PASSWORD)
    @Operation(summary = "Reset a password by token",
            description = "Validates the supplied reset token and saves the new password for the associated account.")
    @PostMapping("/password/reset-link/confirm")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@RequestBody @Valid PasswordResetConfirmRequest request, HttpServletRequest httpRequest) {
        userService.resetPasswordByToken(request.getToken(), request.getNewPwd(), httpRequest);
        return ApiResponse.success();
    }

    /** 웹 요청 → 본인 정보 수정 service 입력(sk 는 service 가 DB 값을 쓰므로 넘기지 않는다, dpi 생략 = false) */
    private static UserUpdateDto toUpdateDto(UserUpdateRequest request) {
        return new UserUpdateDto(request.getPn(), request.getUl(), request.getPif(), request.getDlp(), request.getCt(), Boolean.TRUE.equals(request.getDpi()));
    }

    /** 사용자 프로필 이미지 응답(이미지 없음 = 204) */
    private static ResponseEntity<StreamingResponseBody> toProfileImageResponse(ProfileImageResult result) {
        return result.isEmpty() ? ResponseEntity.noContent().build() : toDownloadResponse(result);
    }

    /** 프로필 이미지 결과 → 스트리밍 다운로드 응답(이미지 없음 = 파일 이름 없는 attachment·0바이트·octet-stream) */
    private static ResponseEntity<StreamingResponseBody> toDownloadResponse(ProfileImageResult result) {
        var response = result.isEmpty()
                ? StreamingDownloadResponse.empty()
                : new StreamingDownloadResponse(
                        ResourceUtil.createContentDisposition(result.fileName()),
                        result.contentLength(),
                        MediaType.parseMediaType(result.contentType()),
                        result.writer()::writeTo);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, response.contentDisposition().toString())
                .header(HttpHeaders.CONTENT_LENGTH, response.contentLength().toString())
                .contentType(response.mediaType())
                .body(response.body());
    }
}
