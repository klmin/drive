package com.thinkfree.drive.api.v1.admin.systemconfig.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

public record GeneralUpdateRequest(
        @Schema(title = "Drive web address", description = "Public web address users use to access this Drive installation.")
        @JsonSetter(nulls = Nulls.AS_EMPTY)
        String clientDomain
) {}