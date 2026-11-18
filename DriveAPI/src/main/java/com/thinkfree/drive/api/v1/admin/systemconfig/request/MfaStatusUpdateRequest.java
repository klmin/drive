package com.thinkfree.drive.api.v1.admin.systemconfig.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record MfaStatusUpdateRequest(
        @Schema(title = "Enable multi-factor authentication", description = "Enable or disable multi-factor authentication for the tenant.")
        @NotNull(message = "MFA 상태는 필수입니다.")
        Boolean enabled
) {
}
