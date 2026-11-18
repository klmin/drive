package com.thinkfree.drive.api.v1.admin.systemconfig.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record IntellectUpdateRequest(
        @Schema(title = "Enable Intellect", description = "Enable or disable Intellect for the tenant.")
        @NotNull(message = "인텔릭트 상태값은 필수 값입니다.")
        Boolean enabled
) {
}
