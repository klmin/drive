package com.thinkfree.drive.oauth2.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** RFC 7591 §3.2.2 등록 에러 응답 (error / error_description). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OAuth2RegistrationErrorResponse(
        String error,
        String errorDescription
) {
}
