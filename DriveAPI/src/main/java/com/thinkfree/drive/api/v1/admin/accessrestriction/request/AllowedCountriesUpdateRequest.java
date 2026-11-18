package com.thinkfree.drive.api.v1.admin.accessrestriction.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AllowedCountriesUpdateRequest(
        @ArraySchema(arraySchema = @Schema(title = "Allowed countries", description = "Add the country codes allowed by the access policy."), schema = @Schema(title = "Country code", description = "One country code allowed by the access policy."))
        @NotNull(message = "허용 국가 목록은 필수 값입니다.")
        List<String> countries
) {
}
