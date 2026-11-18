package com.thinkfree.drive.api.v1.admin.systemconfig.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AccessRestrictionUpdateRequest(
        @Schema(title = "Enable access restrictions", description = "Enable or disable the tenant's configured country and IP access restrictions.")
        @NotNull(message = "접근 제한 상태값은 필수 값입니다.")
        Boolean enabled
) {
}
