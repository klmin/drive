package com.thinkfree.drive.api.v1.admin.systemconfig.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record OfficeUpdateRequest(
        @Schema(title = "Office server address", description = "Base address of the TFO server that this tenant uses to open documents.")
        @Size(max = 2048) String officeDomain,
        @Schema(title = "Office URL authentication", description = "Use ticket to issue a one-time ticket, or legacy for the older URL format.")
        @Pattern(regexp = "ticket|legacy") String officeAuthMode,
        @Schema(title = "Ticket service token", description = "Token that Drive sends to TFO when requesting a document-opening ticket.")
        String officeTicketServiceToken,
        @Schema(title = "HTTP Storage signing key", description = "Key used to verify TFO's signed HTTP Storage callbacks. It must match the key configured in TFO.")
        String officeHttpStorageRequestJwtSecret,
        @Schema(title = "Office connection type", description = "Use onPremise to connect this tenant to your Office server, or none to disable Office integration.")
        @NotBlank @Pattern(regexp = "onPremise|none") String officeConnectionType,
        @Schema(title = "HTTP Storage adapter name", description = "Name of this tenant's HTTP Storage adapter. Match the adapter registered in TFO.")
        @Pattern(regexp = "(?:[A-Za-z0-9][A-Za-z0-9_-]{0,127})?") String officeHttpStorageAdapterName
) {
    public OfficeUpdateRequest(String domain, String mode, String token, String key, String connection) {
        this(domain, mode, token, key, connection, null);
    }
    public OfficeUpdateRequest(String domain, String mode, String token, String key) {
        this(domain, mode, token, key, "onPremise");
    }
    // Validate a boolean instead of the secret field so validation errors cannot echo its value.
    @AssertTrue(message = "인증 정보는 각각 4096자 이내로 입력하세요.")
    @JsonIgnore public boolean isCredentialLengthValid() {
        return (officeTicketServiceToken == null || officeTicketServiceToken.length() <= 4096)
            && (officeHttpStorageRequestJwtSecret == null || officeHttpStorageRequestJwtSecret.length() <= 4096);
    }

    @AssertTrue(message = "On-premise 연결에는 인증 방식과 32바이트 이상의 HTTP Storage 서명키가 필요합니다.")
    @JsonIgnore public boolean isConnectionSettingsValid() {
        return "none".equals(officeConnectionType) || (officeAuthMode != null
            && officeHttpStorageRequestJwtSecret != null
            && officeHttpStorageRequestJwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length >= 32);
    }

    @AssertTrue(message = "Ticket 방식에는 티켓 발급용 서비스 토큰이 필요합니다.")
    @JsonIgnore public boolean isTicketTokenValid() {
        return "none".equals(officeConnectionType) || !"ticket".equals(officeAuthMode) || (officeTicketServiceToken != null && !officeTicketServiceToken.isBlank());
    }
    @AssertTrue(message = "Office 주소에는 http 또는 https 주소만 입력하세요. 사용자 정보, 쿼리, fragment는 사용할 수 없습니다.")
    @JsonIgnore public boolean isOfficeAddressValid() {
        if ("none".equals(officeConnectionType)) return true;
        try {
            var uri = java.net.URI.create(officeDomain);
            return java.util.Set.of("http", "https").contains(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null;
        } catch (Exception e) { return false; }
    }
    @Override public String toString() { return "OfficeUpdateRequest[credentials=REDACTED]"; }
}
