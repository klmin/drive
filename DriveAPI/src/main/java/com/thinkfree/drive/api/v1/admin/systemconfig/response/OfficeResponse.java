package com.thinkfree.drive.api.v1.admin.systemconfig.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Administrator-only response. Never reuse this DTO for public configuration. */
@Schema(name = "AdminOfficeResponse")
public record OfficeResponse(
        String officeConnectionType,
        String officeDomain,
        String officeAuthMode,
        String officeTicketServiceToken,
        String officeHttpStorageAdapterName,
        String officeHttpStorageRequestJwtSecret,
        boolean serviceManaged
) {
    public OfficeResponse(String type, String domain, String mode, String token, String adapter, String secret) {
        this(type, domain, mode, token, adapter, secret, false);
    }
    @Override public String toString() { return "OfficeResponse[credentials=REDACTED]"; }
}
