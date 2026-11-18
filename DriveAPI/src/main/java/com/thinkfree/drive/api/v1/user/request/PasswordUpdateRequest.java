package com.thinkfree.drive.api.v1.user.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PasswordUpdateRequest {
    @Schema(title = "Current password", description = "Current password for the signed-in Drive account.")
    private String currentPwd;
    @NotBlank
    @Schema(title = "New password", description = "New account password: 8–64 characters, including uppercase and lowercase letters, a number and a symbol.")
    private String newPwd;
}
