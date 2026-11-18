package com.thinkfree.drive.api.v1.user.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class PasswordResetConfirmRequest {
    @NotBlank
    @Schema(title = "Password reset token", description = "Token supplied by the password-reset link sent to the user.")
    private String token;
    @NotBlank
    @Schema(title = "New password", description = "New account password: 8–64 characters, including uppercase and lowercase letters, a number and a symbol.")
    private String newPwd;
}
