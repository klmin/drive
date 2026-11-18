package com.thinkfree.drive.api.v1.admin.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.user.enums.RoleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * bean validation만 갖는 순수 DTO — 정규화·동반 필수 검증·dpi 기본값은 application AdminUpdateUserCommand 소유.
 */
@Getter
@AllArgsConstructor
public class AdminUserUpdateRequest {
    @NotBlank
    @Schema(title = "Display name", description = "Name that Drive displays for this user.")
    private String un;              // userName
    @PositiveOrZero
    @Schema(title = "Storage quota (bytes)", description = "Total storage allowance for this user in bytes. USER accounts require a positive quota; ADMIN-only accounts use no user quota.")
    private Long tq;                // totalQuotaByte
    @Schema(title = "Country calling code", description = "Country calling code for the phone number. Send this together with pn, or omit both; omitting both clears the stored phone number and country code.")
    private String ct;              // countryCode
    @Schema(title = "Phone number", description = "User's phone number. Send this together with the country calling code ct, or omit both; omitting both clears the stored phone number and country code.")
    private String pn;              // phoneNumber
    @Schema(title = "Profile image file", description = "Choose a local image to use as the user's profile picture.")
    private MultipartFile pif;      // profileImageFile
    @Schema(title = "Remove profile image", description = "Set true to remove the current profile image. Omit or use false to keep it.")
    private Boolean dpi;            // deleteProfileImage
    @ArraySchema(arraySchema = @Schema(title = "Account roles", description = "Add USER and/or ADMIN for this account. Tenant-admin APIs do not assign SUPER_ADMIN or ANONYMOUS."), schema = @Schema(title = "Account role", description = "USER or ADMIN. Tenant-admin APIs do not grant SUPER_ADMIN or ANONYMOUS."))
    private List<RoleType> roles;   // 교체 후 보유할 역할 전부(USER·ADMIN, 쉼표 구분) — 생략 시 역할 유지
}
