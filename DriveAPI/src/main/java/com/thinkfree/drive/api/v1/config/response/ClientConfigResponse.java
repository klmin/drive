package com.thinkfree.drive.api.v1.config.response;

public record ClientConfigResponse(
    Boolean intellectStatus,
    Boolean officeStatus,
    Boolean shareStatus,
    int tenantCount
) {
}
