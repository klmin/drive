package com.thinkfree.drive.api.v1.admin.accessrestriction.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AllowedIpsUpdateRequest(
        @ArraySchema(arraySchema = @Schema(title = "Allowed IP addresses", description = "Add the IP addresses or IP ranges allowed by the access policy."), schema = @Schema(title = "IP address or range", description = "One allowed IP address or IP range."))
        @NotNull(message = "허용 IP 목록은 필수 값입니다.")
        List<String> ips
) {
}
