package com.thinkfree.drive.api.v1.user.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class PasswordResetRequest {
    @Email
    @NotBlank
    @Schema(title = "Account email", description = "Sign-in account ID (email address) of the Drive user.")
    private String userId;
}
