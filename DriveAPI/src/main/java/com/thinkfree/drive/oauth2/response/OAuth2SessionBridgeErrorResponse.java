package com.thinkfree.drive.oauth2.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** 브리지 에러 응답 (error / error_description) — 코드 계약은 OAuth2SessionBridgeException 참조. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OAuth2SessionBridgeErrorResponse(
        String error,
        String errorDescription
) {
}
